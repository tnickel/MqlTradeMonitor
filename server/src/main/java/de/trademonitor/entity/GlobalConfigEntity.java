package de.trademonitor.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "global_config")
public class GlobalConfigEntity {

    @Id
    private String confKey;
    private String confValue;

    public GlobalConfigEntity() {
    }

    public GlobalConfigEntity(String confKey, String confValue) {
        this.confKey = confKey;
        this.confValue = confValue;
    }

    public String getConfKey() {
        return confKey;
    }

    public void setConfKey(String confKey) {
        this.confKey = confKey;
    }

    public String getConfValue() {
        return confValue;
    }

    public void setConfValue(String confValue) {
        this.confValue = confValue;
    }
}
