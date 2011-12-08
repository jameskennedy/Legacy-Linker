package controllers;

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

    public static void showProgram(@Required final String programName) {
        PCAProgram program = PCAProgram.find("byName", programName).first();
        List<PCAProgramClassLink> linkList = PCAProgramClassLink.find(
                        "methodName is null and program = ? order by linkLines desc", program).fetch();

        GITRepository repository = GITRepository.getMainRepository();
        Set<RepoCommit> commits = getCommits(linkList);

        render(repository, program, linkList, commits);
    }

    private static Set<RepoCommit> getCommits(final List<PCAProgramClassLink> linkList) {
        Set<RepoCommit> commits = new TreeSet<RepoCommit>();

        for (PCAProgramClassLink classLink : linkList) {
            commits.addAll(classLink.file.commits);
        }
        return commits;
    }

}