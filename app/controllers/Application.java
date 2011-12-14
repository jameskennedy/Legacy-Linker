package controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import models.GITRepository;
import models.PCAProgram;
import models.PCAProgramClassLink;
import models.RepoCommit;
import models.RepoFileCommit;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.binding.As;
import play.data.validation.Match;
import play.data.validation.Required;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import services.RepositoryService;

/**
 * Main application controller.
 * 
 * @author james.kennedy
 * 
 */
public class Application extends Controller {

    private static final int RESULTS_PAGE_SIZE = 20;

    /**
     * Index page does PCA program search and results display.
     * 
     * @param programName
     * @param page
     */
    @Transactional(readOnly = true)
    public static void index(@Match("\\s*\\w{0,8}\\s*") final String programName, final int page) {
        GITRepository repository = GITRepository.getMainRepository();

        if (validation.hasErrors()) {
            flash.error("Oops, program name is invalid.");
            render(repository, page);
            return;
        }

        if (StringUtils.isEmpty(programName)) {
            render(repository, page);
            return;
        }

        String programNameNormalized = programName.trim().toUpperCase();
        List<PCAProgram> results = PCAProgram.find("Name like ? order by name", "%" + programNameNormalized + "%")
                        .fetch(page, RESULTS_PAGE_SIZE);

        if (results.size() == 1) {
            showProgram(results.get(0).name);
            // Play framework adds return call here
        }

        if (results.isEmpty() && page > 1) {
            results = null;
        }

        render(repository, programNameNormalized, results, page);
    }

    /**
     * Load data for the given programName and render on the showProgram view.
     * 
     * @param programName
     */
    public static void showProgram(@Required final String programName) {
        Logger.info("Showing page for program %s", programName);

        GITRepository repository = GITRepository.getMainRepository();
        String programNameNormalized = programName.trim().toUpperCase();

        PCAProgram program = PCAProgram.find("byName", programNameNormalized).first();
        if (null == program) {
            Logger.info("Failed to show page, %s not found.", programNameNormalized);
            error(404, "Program " + programNameNormalized + " cannot be found.");
        }

        List<PCAProgramClassLink> linkList = PCAProgramClassLink.find(
                        "methodName is null and program = ? order by linkLines desc", program).fetch();

        for (PCAProgramClassLink classLink : linkList) {
            RepositoryService.calculateAuthorship(classLink);
        }

        List<Long> classSelection = defaultClassSelection(linkList);

        render(repository, program, linkList, classSelection);
    }

    /**
     * Handler of ajax requests to update display of repository commits. Will
     * render a template for dynamic HTML insertion.
     * 
     * @param programName
     * @param classSelection
     */
    public static void relevantCommits(@Required final String programName, @As(",") final List<Long> classSelection) {
        Logger.info("Reloading commits for program %s.", programName);

        List<PCAProgramClassLink> selectedClassLinks = new ArrayList<PCAProgramClassLink>();
        PCAProgram program = getSelectedClassLinksForProgram(programName, classSelection, selectedClassLinks);

        Set<RepoCommit> commits = getFileCommits(program, selectedClassLinks);

        render(commits, program, selectedClassLinks);
    }

    /**
     * Handles ajax requests for the calculated authorship of the given program
     * using the given PCAProgramClassLink id selection list.
     * 
     * @param programName
     * @param classSelection
     * @return Streams out the resulting table in JSON format.
     */
    public static void getProgramAuthorship(@Required final String programName, @As(",") final List<Long> classSelection) {
        List<PCAProgramClassLink> selectedClassLinks = new ArrayList<PCAProgramClassLink>();
        getSelectedClassLinksForProgram(programName, classSelection, selectedClassLinks);

        Map<String, Integer> programAuthorChart = createProgramAuthorChart(selectedClassLinks);
        renderJSON(programAuthorChart);
    }

    private static PCAProgram getSelectedClassLinksForProgram(String programName, final List<Long> classSelection,
                    final List<PCAProgramClassLink> selectedClassLinks) {
        programName = programName.trim().toUpperCase();
        PCAProgram program = PCAProgram.find("byName", programName).first();
        if (null != program) {
            List<PCAProgramClassLink> linkList = PCAProgramClassLink.find(
                            "methodName is null and program = ? order by linkLines desc", program).fetch();

            for (PCAProgramClassLink classLink : linkList) {
                if (classSelection.contains(classLink.getId())) {
                    selectedClassLinks.add(classLink);
                }
            }

        }
        return program;
    }

    private static Map<String, Integer> createProgramAuthorChart(final List<PCAProgramClassLink> selectedClassLinks) {
        Map<String, Integer> result = new HashMap<String, Integer>();
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

    /**
     * With the given program return the set of all it's associated commits
     * restricted to those that affect the selected list of class links.
     * 
     * @param program
     * @param linkList
     * @return Set<RepoCommit>
     */
    private static Set<RepoCommit> getFileCommits(final PCAProgram program, final List<PCAProgramClassLink> linkList) {
        Set<RepoCommit> commits = new TreeSet<RepoCommit>();

        if (null == program) {
            return commits;
        }

        for (PCAProgramClassLink classLink : linkList) {
            for (RepoFileCommit fileCommit : classLink.file.commits) {
                commits.add(fileCommit.commit);
            }
        }

        for (RepoCommit programCommit : program.commitLinks) {
            programCommit.toolTip = program.name + " linked via comment mesage.\n";
        }
        commits.addAll(program.commitLinks);

        return commits;
    }

    /**
     * Initialize a class link id selection by choosing only the ids out of the
     * given linkList where the linkList has adequate program coverage.
     * 
     * @param linkList
     * @return List of PCAProgramClassLink ids.
     */
    private static List<Long> defaultClassSelection(final List<PCAProgramClassLink> linkList) {
        List<Long> selection = new ArrayList<Long>();
        for (PCAProgramClassLink link : linkList) {
            if (link.lineCoverage() > 60f) {
                selection.add(link.getId());
            }
        }
        return selection;
    }

}