package models;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import play.data.validation.Required;
import play.data.validation.Unique;
import play.db.jpa.Model;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public class PCAProgramClassLink extends Model implements Comparable<PCAProgramClassLink> {

    @ManyToOne(optional = false)
    @Required
    public PCAProgram program;

    @ManyToOne(optional = false)
    @Required
    public RepoFile file;

    @OneToMany(mappedBy = "classLink")
    public List<PCAProgramMethodLink> methodLinks;

    @Required
    @Unique("className, methodName")
    public String className;
    public Integer lineTotal = 0;
    public Integer linkLines = 0;

    @Required
    public Boolean indirect = Boolean.FALSE;

    @Override
    public String toString() {
        return program + " <- " + className;
    }

    public String getFullyQualifiedClassName() {
        String fqName = file.path;
        int index = fqName.lastIndexOf(".");
        fqName = fqName.substring(0, index);
        return fqName.replace(".", "/");
    }

    @Override
    public int compareTo(final PCAProgramClassLink other) {
        if (null == other) {
            return -1;
        }
        int result = linkLines.compareTo(other.linkLines);
        if (result == 0 && null != className && null != other.className) {
            result = className.compareTo(other.className);
        }

        return result;

    }

    public float lineCoverage() {
        if (lineTotal == 0) {
            return 0f;
        }

        return Math.round(10000f * linkLines / lineTotal) / 100;
    }
}
