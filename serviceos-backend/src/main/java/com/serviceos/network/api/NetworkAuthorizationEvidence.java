package com.serviceos.network.api;

import java.util.List;

/** 授权决策证据，供审计与幂等完成路径复用。 */
public record NetworkAuthorizationEvidence(List<String> matchedGrantIds, String policyVersion) {}
