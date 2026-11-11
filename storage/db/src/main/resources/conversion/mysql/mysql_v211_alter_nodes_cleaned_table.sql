ALTER TABLE nodes_cleaned
    ADD INDEX nodes_cleaned_policy_key_policy_run_id_idx (policy_key, policy_run_id), ALGORITHM=INPLACE, LOCK=NONE;