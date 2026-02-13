package de.trademonitor.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "magic_mapping")
public class MagicMappingEntity {

    @Id
    private Long magicNumber;
    private String customComment;

    public MagicMappingEntity() {
    }

    public MagicMappingEntity(Long magicNumber, String customComment) {
        this.magicNumber = magicNumber;
        this.customComment = customComment;
    }

    public Long getMagicNumber() {
        return magicNumber;
    }

    public void setMagicNumber(Long magicNumber) {
        this.magicNumber = magicNumber;
    }

    public String getCustomComment() {
        return customComment;
    }

    public void setCustomComment(String customComment) {
        this.customComment = customComment;
    }
}
