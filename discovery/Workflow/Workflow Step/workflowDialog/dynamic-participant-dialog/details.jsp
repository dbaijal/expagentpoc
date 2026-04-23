<%--
  Copyright 1997-2009 Day Management AG
  Barfuesserplatz 6, 4001 Basel, Switzerland
  All Rights Reserved.

  This software is the confidential and proprietary information of
  Day Management AG, ("Confidential Information"). You shall not
  disclose such Confidential Information and shall use it only in
  accordance with the terms of the license agreement you entered into
  with Day.

  ==============================================================================

  Overlay this to include detailed information into step rendering.

--%><%@page session="false" import="com.day.cq.i18n.I18n"%><%
%><%@include file="/libs/foundation/global.jsp"%><%

    String chooser = properties.get("metaData/DYNAMIC_PARTICIPANT", String.class);
    if (chooser == null || chooser.equals("")) {
        chooser = I18n.get(slingRequest, "No participant chooser selected.");
    } 
%>
<div ext:qtip="<%= xssAPI.encodeForHTMLAttr(chooser) %>">
    <div class="dynamic-participant-icon">&nbsp;</div><span class="step-details">[<%= xssAPI.encodeForHTML(chooser) %>]</span>
</div>