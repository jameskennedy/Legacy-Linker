package controllers;

import java.util.List;

import models.PCAProgram;
import models.PCAProgramClassLink;

import org.apache.commons.lang.StringUtils;

import play.data.validation.Match;
import play.data.validation.Required;
import play.mvc.Controller;

public class Application extends Controller {

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

        // Synthetically add the method links with no class link
        // List<PCAProgramMethodLink> orphanMethodLinks =
        // PCAProgramMethodLink.find(
        // "classLink is null and program = ? order by className, linkLines desc",
        // program).fetch();
        // PCAProgramClassLink classLink = null;
        // for (PCAProgramMethodLink methodLink : orphanMethodLinks) {
        // if (classLink == null ||
        // !methodLink.className.equals(classLink.className)) {
        // classLink = new PCAProgramClassLink();
        // classLink.className = methodLink.className;
        // classLink.linkLines = 0;
        // classLink.lineTotal = -1;
        // classLink.program = program;
        // linkList.add(classLink);
        // }
        //
        // classLink.linkLines += methodLink.linkLines;
        // methodLink.classLink = classLink;
        // classLink.methodLinks = new ArrayList<PCAProgramMethodLink>();
        // classLink.methodLinks.add(methodLink);
        // }
        render(program, linkList);
    }

}