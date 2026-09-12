package com.zx.consultant.orchestrator.service;

import com.zx.consultant.orchestrator.IntentResult;

public interface IntentService {

    IntentResult detect(String query);
}
