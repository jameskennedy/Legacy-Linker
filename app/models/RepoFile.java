package models;

import java.io.File;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import play.data.validation.Required;
import play.data.validation.Unique;
import play.db.jpa.Model;

@Entity
public class RepoFile extends Model implements Comparable<RepoFile> {

    @Required
    @ManyToOne
    public GITRepository repository;

    @Required
    @Unique("repository, path")
    public String path;

    public Integer lines;
    public Boolean deletedInHead;

    // @ManyToMany(fetch = FetchType.LAZY) public List<RepoCommit> commits;
    @OneToMany(mappedBy = "file", cascade = { CascadeType.REMOVE }, orphanRemoval = true)
    public List<RepoFileCommit> commits;

    /**
     * Flag to indicate that PCALinkageJob needs to update this RepoFile's PCA
     * links.
     */
    public Boolean linkUpdateNeeded;

    // public List<RepoCommit> commits;

    public RepoFile(final GITRepository repo, final String path) {
        this.path = path;
        this.repository = repo;
    }

    public String getAbsolutePath() {
        return repository.location + File.separator + path;
    }

    public String getName() {
        int index = path.lastIndexOf(File.separator);
        if (index != -1) {
            return path.substring(index + 1);
        }
        return path;
    }

    @Override
    public String toString() {
        return path;
    }

    @Override
    public int compareTo(final RepoFile other) {
        return this.getAbsolutePath().compareTo(other.getAbsolutePath());
    }

}
