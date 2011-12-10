package controllers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import models.GITRepository;
import models.PCAProgram;
import models.PCAProgramClassLink;
import models.RepoCommit;

import org.apache.commons.lang.StringUtils;

import play.data.validation.Match;
import play.data.validation.Required;
import play.db.jpa.Transactional;
import play.mvc.Controller;

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

    public static void showProgram(@Required String programName) {
        GITRepository repository = GITRepository.getMainRepository();
        programName = programName.trim().toUpperCase();

        PCAProgram program = PCAProgram.find("byName", programName).first();
        if (null == program) {
            render(repository);
        }

        List<PCAProgramClassLink> linkList = PCAProgramClassLink.find(
                        "methodName is null and program = ? order by linkLines desc", program).fetch();

        List<Long> classSelection = defaultClassSelection(linkList);

        render(repository, program, linkList, classSelection);
    }

    public static void relevantCommits(@Required String programName) {
        programName = programName.trim().toUpperCase();
        Set<RepoCommit> commits = new HashSet<RepoCommit>();

        PCAProgram program = PCAProgram.find("byName", programName).first();
        if (null != program) {
            List<PCAProgramClassLink> linkList = PCAProgramClassLink.find(
                            "methodName is null and program = ? order by linkLines desc", program).fetch();
            commits = getCommits(program, linkList);
        }

        render(commits, program);
    }

    private static Set<RepoCommit> getCommits(final PCAProgram program, final List<PCAProgramClassLink> linkList) {
        Set<RepoCommit> commits = new TreeSet<RepoCommit>();

        for (RepoCommit programCommit : program.commitLinks) {
            programCommit.toolTip = program.name + " linked via comment mesage.\n";
        }
        commits.addAll(program.commitLinks);

        for (PCAProgramClassLink classLink : linkList) {
            commits.addAll(classLink.file.commits);
        }
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