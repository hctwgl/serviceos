/** 资料模块：拥有 EvidenceSlot、EvidenceItem、EvidenceRevision 与资料集合快照。 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Evidence",
        allowedDependencies = {
                "audit::api", "authorization::api", "configuration::api", "files::api",
                "identity::api", "reliability::api", "reliability::spi", "shared", "task::api"
        })
package com.serviceos.evidence;
