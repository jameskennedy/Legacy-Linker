package models;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.OneToMany;

import play.db.jpa.Model;

@Entity
public class PCAProgram extends Model {

    public String name;
    public String description;
    public String author;

    @OneToMany(mappedBy = "program") public List<PCAProgramClassLink> javaLinks;

    @OneToMany(mappedBy = "program") public List<RepoCommit> commitLinks;

    public PCAProgram(final String name, final String description, final String author) {
        this.name = name;
        this.description = description;
        this.author = author;

    }

    @Override
    public String toString() {
        return name;
    }

}
