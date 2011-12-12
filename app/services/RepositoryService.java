package services;

import java.util.HashMap;
import java.util.List;

import models.PCAProgramClassLink;
import models.RepoCommit;
import models.RepoFile;
import play.Logger;

public class RepositoryService {

    public static void wipeRepositoryData() {
        PCAProgramClassLink.deleteAll();
        List<RepoFile> allRepoFiles = RepoFile.findAll();
        for (RepoFile repoFile : allRepoFiles) {
            repoFile.commits.clear();
            repoFile.save();
        }
        RepoFile.deleteAll();
        RepoCommit.deleteAll();
        // PCAProgram.deleteAll();
        Logger.info("Wiped all repository data.");
    }

    // TODO: Use exactly the lines covered under @legacy for given program
    // git blame -p -w -C -M -l -L1,100
    // triadServicesEJB/ejbModule/com/sasktelinternational/triad/services/common/serviceorder/SignoffCabdisUtilService.java

    public static void calculateAuthorship(final PCAProgramClassLink classLink) {
        if (classLink.authorLinesMap != null) {
            return;
        }

        classLink.authorLinesMap = new HashMap<String, Integer>();

        RepoFile file = classLink.file;

        for (RepoCommit commit : file.commits) {
            String author = commit.author;
            Integer commitLines = commit.linesAdded + commit.linesRemoved;
            Integer authorLines = classLink.authorLinesMap.get(author);
            if (null == authorLines) {
                authorLines = 0;
            }
            authorLines = authorLines + commitLines;
            classLink.authorLinesMap.put(author, authorLines);
        }
    }

}
