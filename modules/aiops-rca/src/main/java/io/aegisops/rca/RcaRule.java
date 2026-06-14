package io.aegisops.rca;

public interface RcaRule {
    String id();

    RcaRuleResult evaluate(RcaAnalysisContext context);
}
