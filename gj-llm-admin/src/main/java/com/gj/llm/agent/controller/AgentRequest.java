package com.gj.llm.agent.controller;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class AgentRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 6151467739879918723L;

    private String question;
    private String type;
}
