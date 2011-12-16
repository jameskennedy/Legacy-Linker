package models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;

import org.hibernate.util.EqualsHelper;

import play.Logger;
import play.db.jpa.GenericModel;
import services.AuthorshipService;
import services.PCALinkageService;

@Entity
public class PCAProgram extends GenericModel {

    @Id
    public String name;
    public String description;
    public String authors;
    public Boolean needsAuthorUpdate = Boolean.TRUE;

    @OneToMany(mappedBy = "program")
    public List<PCAProgramClassLink> javaLinks;

    @OneToMany(mappedBy = "program")
    public List<RepoCommit> commitLinks;

    public PCAProgram(final String name, final String description) {
        this.name = name;
        this.description = description;

    }

    public List<PCAProgramClassLink> getSelectedClassLinks(final List<Long> classSelection) {
        List<PCAProgramClassLink> linkList = getClassLinks();

        if (null != classSelection) {
            List<PCAProgramClassLink> result = new ArrayList<PCAProgramClassLink>();
            for (PCAProgramClassLink classLink : linkList) {
                if (classSelection.contains(classLink.getId())) {
                    result.add(classLink);
                }
            }
            linkList = result;
        }

        return linkList;
    }

    public List<PCAProgramClassLink> getClassLinks() {
        List<PCAProgramClassLink> linkList = PCAProgramClassLink.find(
                        "methodName is null and program = ? order by linkLines desc", this).fetch();
        return linkList;
    }

    public void calculateAuthors() {
        // FIXME: Doing getClassLinks() query twice here
        List<Long> classSelection = PCALinkageService.defaultClassSelection(getClassLinks());
        List<PCAProgramClassLink> selectedClassLinks = getSelectedClassLinks(classSelection);
        String newAuthors = null;
        if (selectedClassLinks.isEmpty()) {
            // TODO: derive author from commits
            // List<RepoCommit> commits = new ArrayList<RepoCommit>();
            // for (RepoCommit commit : commits) {
            //
            // }

            Logger.trace("No author update for %s", name);
        } else {
            SortedMap<User, Integer> programAuthorChart = AuthorshipService
                            .createProgramAuthorChart(selectedClassLinks);
            SortedMap<Integer, String> inverseMap = new TreeMap<Integer, String>();
            for (Entry<User, Integer> entry : programAuthorChart.entrySet()) {
                inverseMap.put(entry.getValue(), entry.getKey().shortName());
            }
            List<String> authorList = new ArrayList(inverseMap.values());
            Collections.reverse(authorList);

            newAuthors = authorList.toString().replaceAll("\\[", "").replaceAll("\\]", "");
        }

        if (!EqualsHelper.equals(authors, newAuthors)) {
            authors = newAuthors;
            Logger.debug("Updated authors of program %s to %s", name, authors);
        }

        this.needsAuthorUpdate = false;
        this.save();
    }

    @Override
    public Object _key() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

}
