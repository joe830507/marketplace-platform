package com.marketplace;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;

/**
 * Layers inside each business module, and calls between modules only through {@code client}
 * (ADR_014, 設計_003 5). The {@code shared} module is organized by function and is exempt.
 */
@AnalyzeClasses(
    packagesOf = MarketplaceApplication.class,
    importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleLayersArchitectureTest {

  private static final String BASE = "com.marketplace.";
  private static final String INTERNAL = "com.marketplace.*.internal..";
  private static final String SHARED = "com.marketplace.shared..";

  /** Modules every module may use directly. */
  private static final Set<String> COMMON_MODULES = Set.of("shared", "openapi");

  @ArchTest
  static final ArchRule internalClassesLiveInALayer =
      classes()
          .that()
          .resideInAPackage(INTERNAL)
          .and()
          .resideOutsideOfPackage(SHARED)
          .should()
          .resideInAnyPackage(
              "..internal.controller..",
              "..internal.service..",
              "..internal.repository..",
              "..internal.entity..",
              "..internal.client..",
              "..internal.config..");

  /**
   * controller → service → repository, entity, client. Event listeners in {@code client} may call
   * services. {@code config} is not a layer: every layer may read settings.
   */
  @ArchTest
  static final ArchRule layersDependDownwards =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .withOptionalLayers(true)
          .layer("Controller")
          .definedBy("com.marketplace.*.internal.controller..")
          .layer("Service")
          .definedBy("com.marketplace.*.internal.service..")
          .layer("Repository")
          .definedBy("com.marketplace.*.internal.repository..")
          .layer("Entity")
          .definedBy("com.marketplace.*.internal.entity..")
          .layer("Client")
          .definedBy("com.marketplace.*.internal.client..")
          .whereLayer("Controller")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Service")
          .mayOnlyBeAccessedByLayers("Controller", "Client")
          .whereLayer("Repository")
          .mayOnlyBeAccessedByLayers("Service")
          .whereLayer("Entity")
          .mayOnlyBeAccessedByLayers("Service", "Repository")
          .whereLayer("Client")
          .mayOnlyBeAccessedByLayers("Service");

  @ArchTest
  static final ArchRule onlyClientsUseOtherModules =
      classes()
          .that()
          .resideInAPackage(INTERNAL)
          .and()
          .resideOutsideOfPackages(SHARED, "..internal.client..")
          .should(useOnlyTheirOwnOrCommonModules());

  @ArchTest
  static final ArchRule generatedApiTypesStayInControllers =
      noClasses()
          .that()
          .resideInAPackage(INTERNAL)
          .and()
          .resideOutsideOfPackages(SHARED, "..internal.controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.marketplace.openapi..");

  private static ArchCondition<JavaClass> useOnlyTheirOwnOrCommonModules() {
    return new ArchCondition<>("use other business modules only through client") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        String own = moduleOf(javaClass);
        for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
          String target = moduleOf(dependency.getTargetClass());
          if (target != null && !target.equals(own) && !COMMON_MODULES.contains(target)) {
            events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
          }
        }
      }
    };
  }

  /** The module a class belongs to, or null for classes outside the modules. */
  private static String moduleOf(JavaClass javaClass) {
    String name = javaClass.getPackageName();
    if (!name.startsWith(BASE)) {
      return null;
    }
    String rest = name.substring(BASE.length());
    int dot = rest.indexOf('.');
    return dot < 0 ? rest : rest.substring(0, dot);
  }
}
