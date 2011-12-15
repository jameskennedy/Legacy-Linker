package models;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;

import play.db.jpa.GenericModel;

@Entity
public class PCAProgram extends GenericModel {

    @Id
    public String name;
    public String description;
    public String author;

    @OneToMany(mappedBy = "program")
    public List<PCAProgramClassLink> javaLinks;

    @OneToMany(mappedBy = "program")
    public List<RepoCommit> commitLinks;

    public PCAProgram(final String name, final String description, final String author) {
        this.name = name;
        this.description = description;
        this.author = author;

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
