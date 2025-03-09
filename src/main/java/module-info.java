module com.evolvlabs.multiuserchatgui {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.apache.derby.engine;
    requires org.apache.derby.commons;
    requires org.apache.derby.client;
    requires jdk.unsupported;

    opens com.evolvlabs.multiuserchatgui to javafx.fxml;
    opens com.evolvlabs.multiuserchatgui.Controllers to javafx.fxml;
    exports com.evolvlabs.multiuserchatgui.Controllers;
}