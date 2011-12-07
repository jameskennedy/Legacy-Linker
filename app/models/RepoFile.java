package models;

import java.io.File;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import play.data.validation.Required;
import play.data.validation.Unique;
import play.db.jpa.Model;

@Entity
public class RepoFile extends Model {

    @Required
    @ManyToOne
    public GITRepository repository;

    @Required
    @Unique("repository, path")
    public String path;
    public Integer lines;

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

}
