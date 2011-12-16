package models;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.Embedded;
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

    @Embedded
    public Map<User, Integer> authorLinesMap = new HashMap<User, Integer>();

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

        return linkLines / (float) lineTotal * 100;
    }

    public String getAuthorLinesMapAsJSON() {
        if (null == authorLinesMap) {
            return "{}";
        }
        StringBuffer buf = new StringBuffer("{");
        boolean first = true;
        for (Entry<User, Integer> entry : authorLinesMap.entrySet()) {
            if (!first) {
                buf.append(",");
            }
            buf.append("'" + entry.getKey().shortName() + "': " + entry.getValue());
            first = false;
        }
        buf.append("}");
        return buf.toString();
    }
}
