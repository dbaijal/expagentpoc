Attention:

If you use this Workflow Step which is a Dynamic Participant Step but also displays the input field of a given Dialog you must do the following:

1) Locate (with CRXDE Light) the Workflow INBOX List JSon Script under:

    /libs/cq/workflow/components/inbox/list/json.jsp

2) Locate these lines:

        } else if (wi.getNode().getType().equals("PARTICIPANT")) {
            participant = wi.getNode().getMetaDataMap().get("PARTICIPANT", String.class);

            if (participant != null && participant.startsWith("/")) {
                Authorizable auth = usrMgr.findByHome(participant);
                if (auth != null) {
                    participant = auth.getName();
                }
            } else {
                participant = usrMgr.get(participant).getName();
            }

            // write dialog path
            String dialogPath = wi.getNode().getMetaDataMap().get("DIALOG_PATH", String.class);
            if (dialogPath != null) {
                writer.key("dialog").value(dialogPath);
            }
        }
        JSONWriterUtil.write(writer, "participant", participant, JSONWriterUtil.WriteMode.AVOID_XSS, xss);

3) Changes these lines to this:

        } else if (wi.getNode().getType().equals("PARTICIPANT")) {
            participant = wi.getNode().getMetaDataMap().get("PARTICIPANT", String.class);

            if (participant != null && participant.startsWith("/")) {
                Authorizable auth = usrMgr.findByHome(participant);
                if (auth != null) {
                    participant = auth.getName();
                }
            } else {
                participant = usrMgr.get(participant).getName();
            }

            // write dialog path
            String dialogPath = wi.getNode().getMetaDataMap().get("DIALOG_PATH", String.class);
            if (dialogPath != null) {
                writer.key("dialog").value(dialogPath);
            }
        } else if (wi.getNode().getType().equals("DYNAMIC_PARTICIPANT")) {
            // write dialog path
            String dialogPath = wi.getNode().getMetaDataMap().get("DIALOG_PATH", String.class);
            if (dialogPath != null) {
                writer.key("dialog").value(dialogPath);
            }
        }
        JSONWriterUtil.write(writer, "participant", participant, JSONWriterUtil.WriteMode.AVOID_XSS, xss);

4) Now you can use this step

