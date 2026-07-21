/**
 * 网点与师傅目录模块：合作组织、ServiceNetwork、成员、师傅档案/关系与资质。
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Network and Technician Directory",
        allowedDependencies = {"shared", "jooq", "identity :: api", "reliability :: api", "audit :: api"}
)
package com.serviceos.network;
