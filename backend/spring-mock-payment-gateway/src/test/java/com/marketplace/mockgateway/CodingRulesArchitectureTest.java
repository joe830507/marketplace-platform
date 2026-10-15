package com.marketplace.mockgateway;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noCodeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitCallTarget;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;

/**
 * Coding conventions checked on production code (設計_006 4.1, 6.2, applied to the mock gateway as
 * well).
 */
@AnalyzeClasses(
    packagesOf = MockPaymentGatewayApplication.class,
    importOptions = ImportOption.DoNotIncludeTests.class)
class CodingRulesArchitectureTest {

  /** The one class allowed to read the system clock. */
  private static final String CLOCK_PROVIDER = "com.marketplace.mockgateway.TimeConfiguration";

  private static final Set<String> FLOATING_POINT_TYPES =
      Set.of("double", "float", "java.lang.Double", "java.lang.Float");

  private static final DescribedPredicate<JavaCall<?>> READ_THE_SYSTEM_TIME =
      new DescribedPredicate<>("read the system time instead of the injected clock") {
        @Override
        public boolean test(JavaCall<?> call) {
          CodeUnitCallTarget target = call.getTarget();
          String owner = target.getOwner().getName();
          String name = target.getName();
          boolean noParameters = target.getRawParameterTypes().isEmpty();
          return (owner.startsWith("java.time.") && name.equals("now") && noParameters)
              || (owner.equals("java.time.Clock")
                  && (name.equals("systemUTC") || name.equals("systemDefaultZone")))
              || (owner.equals("java.lang.System") && name.equals("currentTimeMillis"))
              || (owner.equals("java.util.Date") && name.equals("<init>") && noParameters);
        }
      };

  private static final DescribedPredicate<JavaClass> FLOATING_POINT =
      new DescribedPredicate<>("a floating point type") {
        @Override
        public boolean test(JavaClass type) {
          return FLOATING_POINT_TYPES.contains(type.getName());
        }
      };

  private static final DescribedPredicate<JavaCodeUnit> FLOATING_POINT_SIGNATURE =
      new DescribedPredicate<>("use a floating point type as parameter or return type") {
        @Override
        public boolean test(JavaCodeUnit codeUnit) {
          return FLOATING_POINT.test(codeUnit.getRawReturnType())
              || codeUnit.getRawParameterTypes().stream().anyMatch(FLOATING_POINT);
        }
      };

  @ArchTest
  static final ArchRule codeUsesTheInjectedClock =
      noClasses()
          .that()
          .doNotHaveFullyQualifiedName(CLOCK_PROVIDER)
          .should()
          .callCodeUnitWhere(READ_THE_SYSTEM_TIME);

  @ArchTest
  static final ArchRule noFloatingPointFields = noFields().should().haveRawType(FLOATING_POINT);

  @ArchTest
  static final ArchRule noFloatingPointSignatures =
      noCodeUnits().should(ArchCondition.from(FLOATING_POINT_SIGNATURE));
}
