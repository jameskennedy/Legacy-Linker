package controllers;

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
    public static void index(@Match("\\s*\\w{0,8}\\s*") String programName) {
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

        List<PCAProgram> results = PCAProgram.find("Name like ? order by name", "%" + programName + "%").fetch(1, 200);

        render(programName, results);
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

        render(repository, program, linkList);
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

        render(commits);
    }

    private static Set<RepoCommit> getCommits(final PCAProgram program, final List<PCAProgramClassLink> linkList) {
        Set<RepoCommit> commits = new TreeSet<RepoCommit>();

        commits.addAll(program.commitLinks);

        for (PCAProgramClassLink classLink : linkList) {
            commits.addAll(classLink.file.commits);
        }
        return commits;
    }

}