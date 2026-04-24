module org.example {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;
    requires java.desktop;
    requires java.sql;
    requires jakarta.persistence;
    requires org.hibernate.orm.core;
    requires jbcrypt;
    requires java.naming;
    requires org.controlsfx.controls;
    
    opens org.example.client.controller to javafx.fxml;
    opens org.example.common.model to org.hibernate.orm.core;
    opens org.example.common.network to com.google.gson;
    exports org.example.client.network;
    exports org.example.client;
    exports org.example.server;
}
