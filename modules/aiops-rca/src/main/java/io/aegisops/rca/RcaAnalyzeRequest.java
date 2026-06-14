package io.aegisops.rca;

public record RcaAnalyzeRequest(
        Boolean force
) {
    public boolean forceEnabled() {
        return Boolean.TRUE.equals(force);
    }
}
