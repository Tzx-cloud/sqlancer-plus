package sqlancer.general.learner;

import sqlancer.general.GeneralProvider.GeneralGlobalState;

public class GeneralStringBuilder <E extends GeneralFragments>{

    private StringBuilder sb;
    private E fragments;
    private GeneralGlobalState state;

    public GeneralStringBuilder(GeneralGlobalState globalState, E fragments) {
        this.sb = new StringBuilder();
        this.state = globalState;
        this.fragments = fragments;
    }

    public void append(String str) {
        sb.append(str);
    }

    public void append(Object obj) {
        sb.append(obj);
    }

    public void append(Object obj, int index) {
        sb.append(obj);
        sb.append(fragments.get(index, state));
    }

    public String toString() {
        return sb.toString();
    }
}
