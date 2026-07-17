package com.serviceos.authorization.api;

import java.time.Instant;
import java.util.List;

public record DelegationPage(List<DelegationView> items, Instant asOf) {
}
