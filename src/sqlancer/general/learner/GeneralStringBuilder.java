package sqlancer.general.learner;

import sqlancer.general.GeneralProvider.GeneralGlobalState;

public class GeneralStringBuilder <E extends GeneralElements>{

    private StringBuilder sb;
    private E elements;
    private GeneralGlobalState state;

    public GeneralStringBuilder(GeneralGlobalState globalState, E elements) {
        this.sb = new StringBuilder();
        this.state = globalState;
        this.elements = elements;
    }

    public void append(String str) {
        sb.append(str);
    }

    public void append(Object obj) {
        sb.append(obj);
    }

    public void append(Object obj, int index) {
        sb.append(obj);
        sb.append(elements.get(index, state));
    }

    public String toString() {
        return sb.toString();
    }
}
