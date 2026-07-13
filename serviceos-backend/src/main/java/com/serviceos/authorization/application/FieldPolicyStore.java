package com.serviceos.authorization.application;

import java.util.List;
import java.util.Set;

public interface FieldPolicyStore {
    FieldPolicyMatch resolve(
            String tenantId,
            List<String> matchedGrantIds,
            String capability,
            String resourceType,
            Set<String> fieldCodes
    );
}
