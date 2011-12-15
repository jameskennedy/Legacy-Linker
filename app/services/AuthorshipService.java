package services;

import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import models.PCAProgramClassLink;

public class AuthorshipService {

    /**
     * Create a an aggregated distribution map representing the number of lines
     * each author has contributed to the collection of
     * {@link PCAProgramClassLink} given.
     * 
     * @param selectedClassLinks
     * @return SortedMap<Author, Lines added + lines removed> sorted by author
     *         user id.
     */
    public static SortedMap<String, Integer> createProgramAuthorChart(final List<PCAProgramClassLink> selectedClassLinks) {
        SortedMap<String, Integer> result = new TreeMap<String, Integer>();
        for (PCAProgramClassLink classLink : selectedClassLinks) {
            float coverage = classLink.lineCoverage();
            // TODO: expensive to always calculate on the fly?
            RepositoryService.calculateAuthorship(classLink);
            for (Entry<String, Integer> entry : classLink.authorLinesMap.entrySet()) {
                String author = entry.getKey();
                Integer lines = result.get(author);
                if (null == lines) {
                    lines = 0;
                }

                // TODO Rough approximation. need to tally actually lines that
                // contribute only
                lines += Math.round(coverage / 100f * entry.getValue());
                result.put(author, lines);
            }
        }
        return result;
    }

}
