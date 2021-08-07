package at.tugraz.iaik.scos.abstraction.mealy;

import de.learnlib.api.SUL;
import de.learnlib.api.exception.SULException;

public class SUL1 implements SUL<Integer, Boolean> {

    Boolean isEven = false;
    
    @Override
    public void pre() {
        isEven = false;
    }

    @Override
    public void post() {
        isEven = false;
    }

    @Override
    public Boolean step(Integer in) {
        isEven = (in % 2 == 0);
        return isEven;
    }
    
}
