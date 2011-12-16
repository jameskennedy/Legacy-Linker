package controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import models.GITRepository;
import models.PCAProgram;
import models.PCAProgramClassLink;
import models.RepoCommit;
import models.RepoFileCommit;
import models.User;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.data.binding.As;
import play.data.validation.Match;
import play.data.validation.Required;
import play.db.jpa.Transactional;
import play.mvc.Controller;
import services.AuthorshipService;
import services.PCALinkageService;
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

        PCAProgram program = PCAProgram.findById(programName);
        if (null == program) {
            Logger.info("Failed to show page, %s not found.", programNameNormalized);
            error(404, "Program " + programNameNormalized + " cannot be found.");
        }

        List<PCAProgramClassLink> linkList = program.getClassLinks();

        for (PCAProgramClassLink classLink : linkList) {
            RepositoryService.calculateAuthorship(classLink);
        }

        List<Long> classSelection = PCALinkageService.defaultClassSelection(linkList);

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

        PCAProgram program = PCAProgram.findById(programName.trim().toUpperCase());
        List<PCAProgramClassLink> selectedClassLinks = program.getSelectedClassLinks(classSelection);

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
        new ArrayList<PCAProgramClassLink>();
        PCAProgram program = PCAProgram.findById(programName.trim().toUpperCase());
        List<PCAProgramClassLink> selectedClassLinks = program.getSelectedClassLinks(classSelection);

        Map<User, Integer> programAuthorChart = AuthorshipService.createProgramAuthorChart(selectedClassLinks);
        renderJSON(programAuthorChart);
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

}