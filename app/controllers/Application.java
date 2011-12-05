package controllers;

import java.util.List;

import models.PCAProgram;

import org.apache.commons.lang.StringUtils;

import play.data.validation.Match;
import play.mvc.Controller;

public class Application extends Controller {
	
    public static void index(@Match("\\s*\\w{0,8}\\s*") String programName) {
    	if(validation.hasErrors()) {
            flash.error("Oops, program name is invalid.");
            render();
            return;
        }
    	
    	if (StringUtils.isEmpty(programName)) {
    		render();
    		return;
    	}
    	
    	programName = programName.trim();
    	
        List<PCAProgram> results = PCAProgram.find("Name like ? order by name", "%" + programName + "%").fetch(1, 20);
    	
        render(programName, results);
    }

}