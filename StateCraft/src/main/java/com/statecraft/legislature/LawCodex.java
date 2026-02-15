package com.statecraft.legislature;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Searchable collection of all enacted laws for a nation
 * Provides query methods by keyword, date, category, author
 */
public class LawCodex {

    private final UUID nationId;
    private final List<Law> laws;
    private int nextLawNumber;
    private int nextExecutiveOrderNumber;
    private int currentYear;

    public LawCodex(UUID nationId) {
        this.nationId = nationId;
        this.laws = new ArrayList<>();
        this.nextLawNumber = 1;
        this.nextExecutiveOrderNumber = 1;
        this.currentYear = LocalDate.now().getYear();
    }

    /**
     * Generate the next law number for a given type
     */
    public String generateLawNumber(Law.Type type) {
        int year = LocalDate.now().getYear();

        // Reset counters if year changed
        if (year != currentYear) {
            currentYear = year;
            nextLawNumber = 1;
            nextExecutiveOrderNumber = 1;
        }

        String number;
        if (type == Law.Type.NATION_LAW) {
            number = String.format("%s-%d-%03d", type.getPrefix(), year, nextLawNumber++);
        } else {
            number = String.format("%s-%d-%03d", type.getPrefix(), year, nextExecutiveOrderNumber++);
        }

        return number;
    }

    /**
     * Add a new law to the codex
     */
    public void addLaw(Law law) {
        laws.add(law);
        // Sort by enacted time descending (newest first)
        laws.sort((a, b) -> Long.compare(b.getEnactedTime(), a.getEnactedTime()));
    }

    /**
     * Get all laws (newest first)
     */
    public List<Law> getAllLaws() {
        return Collections.unmodifiableList(laws);
    }

    /**
     * Get active (non-repealed) laws
     */
    public List<Law> getActiveLaws() {
        return laws.stream()
            .filter(law -> !law.isRepealed())
            .collect(Collectors.toList());
    }

    /**
     * Search laws by query string
     */
    public List<Law> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllLaws();
        }

        return laws.stream()
            .filter(law -> law.matchesSearch(query))
            .collect(Collectors.toList());
    }

    /**
     * Search laws by category
     */
    public List<Law> searchByCategory(PolicyType.Category category) {
        return laws.stream()
            .filter(law -> law.getPolicyChanges().keySet().stream()
                .anyMatch(policy -> policy.getCategory() == category))
            .collect(Collectors.toList());
    }

    /**
     * Search laws by type (NL or EO)
     */
    public List<Law> searchByType(Law.Type type) {
        return laws.stream()
            .filter(law -> law.getType() == type)
            .collect(Collectors.toList());
    }

    /**
     * Search laws by author
     */
    public List<Law> searchByAuthor(String authorName) {
        String lowerAuthor = authorName.toLowerCase();
        return laws.stream()
            .filter(law -> law.getAuthorName().toLowerCase().contains(lowerAuthor))
            .collect(Collectors.toList());
    }

    /**
     * Search laws enacted within a date range
     */
    public List<Law> searchByDateRange(long startTime, long endTime) {
        return laws.stream()
            .filter(law -> law.getEnactedTime() >= startTime && law.getEnactedTime() <= endTime)
            .collect(Collectors.toList());
    }

    /**
     * Search laws from a specific year
     */
    public List<Law> searchByYear(int year) {
        String yearPrefix = "-" + year + "-";
        return laws.stream()
            .filter(law -> law.getLawNumber().contains(yearPrefix))
            .collect(Collectors.toList());
    }

    /**
     * Get a specific law by its number
     */
    public Law getLawByNumber(String lawNumber) {
        return laws.stream()
            .filter(law -> law.getLawNumber().equalsIgnoreCase(lawNumber))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get recent laws (last N)
     */
    public List<Law> getRecentLaws(int count) {
        return laws.stream()
            .limit(count)
            .collect(Collectors.toList());
    }

    /**
     * Get statistics about the codex
     */
    public CodexStats getStats() {
        int totalLaws = laws.size();
        int activeLaws = (int) laws.stream().filter(l -> !l.isRepealed()).count();
        int nationLaws = (int) laws.stream().filter(l -> l.getType() == Law.Type.NATION_LAW).count();
        int executiveOrders = (int) laws.stream().filter(l -> l.getType() == Law.Type.EXECUTIVE_ORDER).count();
        int vetoProofLaws = (int) laws.stream().filter(Law::wasVetoProof).count();

        return new CodexStats(totalLaws, activeLaws, nationLaws, executiveOrders, vetoProofLaws);
    }

    // NBT Serialization
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("nationId", nationId);
        tag.putInt("nextLawNumber", nextLawNumber);
        tag.putInt("nextExecutiveOrderNumber", nextExecutiveOrderNumber);
        tag.putInt("currentYear", currentYear);

        ListTag lawsList = new ListTag();
        for (Law law : laws) {
            lawsList.add(law.save());
        }
        tag.put("laws", lawsList);

        return tag;
    }

    public static LawCodex load(CompoundTag tag) {
        UUID nationId = tag.getUUID("nationId");
        LawCodex codex = new LawCodex(nationId);
        codex.nextLawNumber = tag.getInt("nextLawNumber");
        codex.nextExecutiveOrderNumber = tag.getInt("nextExecutiveOrderNumber");
        codex.currentYear = tag.getInt("currentYear");

        ListTag lawsList = tag.getList("laws", Tag.TAG_COMPOUND);
        for (int i = 0; i < lawsList.size(); i++) {
            codex.laws.add(Law.load(lawsList.getCompound(i)));
        }

        return codex;
    }

    /**
     * Statistics about the law codex
     */
    public record CodexStats(
        int totalLaws,
        int activeLaws,
        int nationLaws,
        int executiveOrders,
        int vetoProofLaws
    ) {}
}

