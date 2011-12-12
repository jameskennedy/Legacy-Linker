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

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.Match;
import play.data.validation.Required;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import services.RepositoryService;

public class Application extends Controller {

    @Transactional(readOnly = true)
    public static void index(@Match("\\s*\\w{0,8}\\s*") String programName, final int page) {
        if (validation.hasErrors()) {
            flash.error("Oops, program name is invalid.");
            render();
            return;
        }

        if (StringUtils.isEmpty(programName)) {
            render();
            return;
        }

        programName = programName.trim().toUpperCase();
        List<PCAProgram> results = PCAProgram.find("Name like ? order by name", "%" + programName + "%")
                        .fetch(page, 20);

        if (results.size() == 1) {
            showProgram(results.get(0).name);
        }

        if (results.isEmpty() && page > 1) {
            results = null;
        }

        render(programName, results, page);
    }

    /**
     * Load data for the given programName and render on the showProgram view.
     * 
     * @param programName
     */
    public static void showProgram(@Required String programName) {
        GITRepository repository = GITRepository.getMainRepository();
        programName = programName.trim().toUpperCase();

        PCAProgram program = PCAProgram.find("byName", programName).first();
        if (null == program) {
            error(404, "Program " + programName + " cannot be found.");
        }

        List<PCAProgramClassLink> linkList = PCAProgramClassLink.find(
                        "methodName is null and program = ? order by linkLines desc", program).fetch();

        for (PCAProgramClassLink classLink : linkList) {
            RepositoryService.calculateAuthorship(classLink);
        }

        List<Long> classSelection = defaultClassSelection(linkList);

        render(repository, program, linkList, classSelection);
    }

    public static void relevantCommits(@Required final String programName, @As(",") final List<Long> classSelection) {
        List<PCAProgramClassLink> selectedClassLinks = new ArrayList<PCAProgramClassLink>();
        PCAProgram program = getSelectedClassLinksForProgram(programName, classSelection, selectedClassLinks);

        Set<RepoCommit> commits = getCommits(program, selectedClassLinks);

        render(commits, program);
    }

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

                // TODO Ugly approximation need to tally actualy lines that
                // contribute only
                lines += Math.round(coverage / 100 * entry.getValue());
                result.put(author, lines);
            }
        }
        return result;
    }

    private static Set<RepoCommit> getCommits(final PCAProgram program, final List<PCAProgramClassLink> linkList) {
        Set<RepoCommit> commits = new TreeSet<RepoCommit>();

        if (null == program) {
            return commits;
        }

        for (PCAProgramClassLink classLink : linkList) {
            commits.addAll(classLink.file.commits);
        }

        for (RepoCommit programCommit : program.commitLinks) {
            programCommit.toolTip = program.name + " linked via comment mesage.\n";
        }
        commits.addAll(program.commitLinks);

        return commits;
    }

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