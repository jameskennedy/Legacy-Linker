package jobs;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import models.PCAProgram;
import play.Logger;
import play.jobs.Job;

public class ImportPCAProgramsJob extends Job {

    @Override
    public void doJob() {
        File rootDir = new File("/Users/james.kennedy/Documents/workspace/MartensSource");

        if (PCAProgram.all().first() != null) {
            return;
        }

        if (rootDir == null || !rootDir.exists() || !rootDir.isDirectory()) {
            throw new IllegalArgumentException(rootDir + " is not anexisting root direcotry.");
        }

        Logger.debug("Loading Cobol programs to db...");

        Stack<File> fileStack = new Stack<File>();
        fileStack.push(rootDir);

        Set<String> programNames = new HashSet<String>();

        int filesScanned = 0;
        while (!fileStack.isEmpty()) {
            File file = fileStack.pop();
            if (file.isDirectory()) {
                fileStack.addAll(Arrays.asList(file.listFiles()));
                filesScanned = 0;
                continue;
            }

            if (!file.isFile() || file.isHidden() || !file.exists()) {
                continue;
            }

            String fileName = file.getName();
            if (fileName.endsWith(".cpy") || fileName.endsWith(".ccp") || fileName.endsWith(".cbl")
                            || fileName.endsWith(".proc")) {
                programNames.add(fileName.substring(0, fileName.lastIndexOf(".")));
            }
        }

        filesScanned = 0;
        for (String program : programNames) {
            PCAProgram programEntity = new PCAProgram(program, null);
            programEntity.save();
            if (filesScanned++ % 500 == 0) {
                Logger.debug("Cobol programs to save: %d", programNames.size() - filesScanned);
            }
        }
    }
}
