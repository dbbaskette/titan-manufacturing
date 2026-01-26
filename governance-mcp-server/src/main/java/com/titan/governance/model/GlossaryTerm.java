package com.titan.governance.model;

import java.util.List;

/**
 * A business glossary term definition.
 */
public record GlossaryTerm(
    String term,
    String definition,
    String domain,
    List<String> synonyms,
    List<String> relatedTerms,
    List<String> usedInTables,
    String owner,
    String summary
) {}
