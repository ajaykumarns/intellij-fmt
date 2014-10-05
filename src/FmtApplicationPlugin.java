import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Created by anadathur on 10/4/14.
 */
public class FmtApplicationPlugin implements ApplicationComponent
{

    public void initComponent() {
/*        Messages.showMessageDialog("Welcome to IntelliJ IDEA!\nToday: " + new Date(),
                "FmtPlugin", Messages.getInformationIcon());*/
    }

    public void disposeComponent() 
    {
/*
        Messages.showMessageDialog("Thank you for using IntelliJ IDEA!", "FmtPlugin", Messages.getInformationIcon());
*/
    }

    @NotNull
    public String getComponentName() {
        return "FmtApplicationPlugin";
    }
}
