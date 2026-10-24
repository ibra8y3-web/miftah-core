# Architecture
app (Compose UI, ViewModel) -> core:capability (CapabilityEngine, PackageInventory, DeviceInfo) -> core:model (types, AppQuery, OperationResult).
UI reads only from ViewModel state; no system calls inside composables.
