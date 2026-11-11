package com.miftah.core.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.miftah.core.net.Blocklist
import com.miftah.core.net.DnsPacket
import com.miftah.core.net.Ipv4Udp
import com.miftah.core.net.UdpDatagram
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

enum class VpnMode { ADBLOCK, FIREWALL }

data class VpnConfig(val mode: VpnMode, val excluded: List<String>, val blocked: List<String>, val upstream: Upstream)

data class VpnUiState(
    val running: Boolean = false,
    val mode: VpnMode? = null,
    val total: Long = 0,
    val blocked: Long = 0,
    val recentBlocked: List<String> = emptyList(),
    val error: String? = null,
)

/** Process-wide VPN status, written by the service and read by the UI. */
object VpnRuntime {
    val state = MutableStateFlow(VpnUiState())
}

/**
 * Local VPN with two modes (Android allows only one VPN at a time):
 *  - ADBLOCK: only DNS (to a virtual resolver) enters the tunnel; blocked domains get NXDOMAIN, the rest is forwarded.
 *  - FIREWALL: only the chosen apps enter the tunnel and everything they send is dropped.
 */
class MiftahVpnService : VpnService() {
    @Volatile private var running = false
    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private var pool: ExecutorService? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val i = intent ?: return START_NOT_STICKY
        if (i.action != ACTION_START) return START_NOT_STICKY
        val mode = VpnMode.entries.firstOrNull { it.name == i.getStringExtra(EXTRA_MODE) }
        if (mode == null) { fail("invalid mode"); return START_NOT_STICKY }
        val upstream = Upstream.entries.firstOrNull { it.name == i.getStringExtra(EXTRA_UPSTREAM) } ?: Upstream.SYSTEM
        start(mode, i.getStringArrayListExtra(EXTRA_EXCLUDED).orEmpty(), i.getStringArrayListExtra(EXTRA_BLOCKED).orEmpty(), upstream)
        return START_NOT_STICKY
    }

    private fun start(mode: VpnMode, excluded: List<String>, blocked: List<String>, upstream: Upstream) {
        stopVpn(updateState = false)
        if (mode == VpnMode.FIREWALL && blocked.isEmpty()) { fail("the firewall app list is empty"); return }
        val blocklist = if (mode == VpnMode.ADBLOCK) BlocklistFiles.load(this) else Blocklist(emptyList())
        val upstreams = if (mode == VpnMode.ADBLOCK) resolveUpstreams(upstream) else emptyList()

        val builder = Builder().setSession(getString(R.string.app_name)).setMtu(1500).addAddress(VPN_ADDR, 32)
        if (mode == VpnMode.ADBLOCK) {
            builder.addDnsServer(DNS_ADDR).addRoute(DNS_ADDR, 32)
            excluded.forEach { pkg ->
                try { builder.addDisallowedApplication(pkg) } catch (e: PackageManager.NameNotFoundException) { /* app was removed: nothing to exclude */ }
            }
        } else {
            builder.addAddress("fd00:6d69:6674::2", 128).addRoute("0.0.0.0", 0).addRoute("::", 0)
            blocked.forEach { pkg ->
                try { builder.addAllowedApplication(pkg) } catch (e: PackageManager.NameNotFoundException) { /* app was removed: nothing to block */ }
            }
        }
        val pfd = try { builder.establish() } catch (e: IllegalStateException) { null } catch (e: IllegalArgumentException) { null }
        if (pfd == null) { fail("VPN permission is missing or was revoked"); return }

        tun = pfd
        running = true
        VpnRuntime.state.value = VpnUiState(running = true, mode = mode)
        pool = Executors.newFixedThreadPool(6)
        worker = Thread({ loop(pfd, mode, blocklist, upstreams) }, "miftah-vpn").also { it.start() }
    }

    private fun loop(pfd: ParcelFileDescriptor, mode: VpnMode, blocklist: Blocklist, upstreams: List<InetAddress>) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (running) {
                val n = input.read(buf)
                if (n < 0) break
                if (n == 0 || mode == VpnMode.FIREWALL) continue
                val dg = Ipv4Udp.parse(buf, n) ?: continue
                if (dg.dst != DNS_IP || dg.dstPort != 53) continue
                try {
                    pool?.execute { handle(dg, blocklist, upstreams, output) }
                } catch (e: RejectedExecutionException) {
                    break
                }
            }
        } catch (e: IOException) {
            if (running) fail("VPN stopped: ${e.message}")
        }
    }

    private fun handle(dg: UdpDatagram, blocklist: Blocklist, upstreams: List<InetAddress>, out: FileOutputStream) {
        val q = DnsPacket.parseQuestion(dg.payload)
        val hit = q != null && blocklist.isBlocked(q.name)
        val reply: ByteArray? = if (hit && q != null) DnsPacket.nxdomain(dg.payload, q) else forward(dg.payload, upstreams)
        VpnRuntime.state.update { s ->
            s.copy(
                total = s.total + 1,
                blocked = s.blocked + if (hit) 1 else 0,
                recentBlocked = if (hit && q != null) (listOf(q.name) + s.recentBlocked).take(40) else s.recentBlocked,
            )
        }
        if (reply == null) return // upstream unreachable: no reply, so the app sees a real DNS failure
        val pkt = Ipv4Udp.build(dg.dst, dg.src, 53, dg.srcPort, reply)
        try {
            synchronized(out) { out.write(pkt) }
        } catch (e: IOException) {
            // tunnel closed while stopping
        }
    }

    private fun forward(payload: ByteArray, upstreams: List<InetAddress>): ByteArray? {
        for (server in upstreams) {
            try {
                DatagramSocket().use { s ->
                    if (!protect(s)) return@use
                    s.soTimeout = 3000
                    s.send(DatagramPacket(payload, payload.size, server, 53))
                    val buf = ByteArray(4096)
                    val resp = DatagramPacket(buf, buf.size)
                    s.receive(resp)
                    return buf.copyOf(resp.length)
                }
            } catch (e: IOException) {
                // try the next upstream server
            }
        }
        return null
    }

    private fun resolveUpstreams(u: Upstream): List<InetAddress> {
        val ips = when (u) {
            Upstream.CLOUDFLARE -> listOf("1.1.1.1", "1.0.0.1")
            Upstream.GOOGLE -> listOf("8.8.8.8", "8.8.4.4")
            Upstream.QUAD9 -> listOf("9.9.9.9", "149.112.112.112")
            Upstream.SYSTEM -> emptyList()
        }
        if (ips.isNotEmpty()) return ips.map { InetAddress.getByName(it) }
        val system = systemDns().sortedBy { it is Inet6Address }
        return system.ifEmpty { listOf(InetAddress.getByName("1.1.1.1")) }
    }

    @Suppress("DEPRECATION")
    private fun systemDns(): List<InetAddress> {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return emptyList()
        return cm.allNetworks
            .filter { n ->
                cm.getNetworkCapabilities(n)?.let {
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                } == true
            }
            .flatMap { cm.getLinkProperties(it)?.dnsServers.orEmpty() }
            .distinct()
    }

    private fun fail(message: String) {
        stopVpn(updateState = false)
        VpnRuntime.state.update { it.copy(running = false, mode = null, error = message) }
        stopSelf()
    }

    private fun stopVpn(updateState: Boolean = true) {
        running = false
        pool?.shutdownNow()
        pool = null
        try { tun?.close() } catch (e: IOException) { /* already closed */ }
        tun = null
        worker?.interrupt()
        worker = null
        if (updateState) VpnRuntime.state.update { it.copy(running = false, mode = null) }
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.miftah.core.app.vpn.START"
        const val EXTRA_MODE = "mode"
        const val EXTRA_UPSTREAM = "upstream"
        const val EXTRA_EXCLUDED = "excluded"
        const val EXTRA_BLOCKED = "blocked"
        private const val VPN_ADDR = "10.255.255.2"
        private const val DNS_ADDR = "10.255.255.1"
        private val DNS_IP = Ipv4Udp.ip(10, 255, 255, 1)
    }
}
