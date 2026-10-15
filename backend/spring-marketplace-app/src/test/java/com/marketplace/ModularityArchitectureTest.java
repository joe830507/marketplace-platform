package com.marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Module boundaries: no illegal or cyclic dependencies between modules, and no access to another
 * module's internal packages (設計_006 6.2).
 */
class ModularityArchitectureTest {

  @Test
  void modulesRespectTheirBoundaries() {
    ApplicationModules.of(MarketplaceApplication.class).verify();
  }
}
