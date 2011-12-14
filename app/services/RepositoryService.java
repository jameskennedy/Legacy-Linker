package services;

import java.util.HashMap;

import models.PCAProgramClassLink;
import models.RepoCommit;
import models.RepoFile;
import models.RepoFileCommit;
import play.Logger;

public class RepositoryService {

    public static void wipeRepositoryData() {
        PCAProgramClassLink.deleteAll();
        RepoFileCommit.deleteAll();
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

        for (RepoFileCommit fileCommit : file.commits) {
            String author = fileCommit.commit.author;
            Integer commitLines = fileCommit.linesAdded + fileCommit.linesRemoved;
            Integer authorLines = classLink.authorLinesMap.get(author);
            if (null == authorLines) {
                authorLines = 0;
            }
            authorLines = authorLines + commitLines;
            classLink.authorLinesMap.put(author, authorLines);
        }
    }

}
