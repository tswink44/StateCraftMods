package dev.statecraft.api.ui;

public enum ClaimMapMode {
    CLAIM("Claim chunks", "chunk claim <nation> <chunks>", "nation", ""),
    ASSIGN_STATE("Assign territory to states", "chunk assignstate <nation> <chunks> <state_or_none>", "nation", "state_or_none"),
    ASSIGN_CITY("Assign territory to cities", "chunk assigncity <state> <chunks> <city_or_none>", "state", "city_or_none");

    private final String title;
    private final String template;
    private final String governmentField;
    private final String targetField;

    ClaimMapMode(String title, String template, String governmentField, String targetField) {
        this.title = title;
        this.template = template;
        this.governmentField = governmentField;
        this.targetField = targetField;
    }

    public String title() { return title; }
    public String template() { return template; }
    public String governmentField() { return governmentField; }
    public String targetField() { return targetField; }
}
