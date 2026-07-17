/**
 * 组织目录模块：企业 Organization、OrgUnit closure、任职与外部同步收据。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Organization",
        allowedDependencies = {"shared", "identity :: api", "reliability :: api", "audit :: api"}
)
package com.serviceos.organization;
