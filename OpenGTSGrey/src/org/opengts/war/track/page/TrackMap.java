// ----------------------------------------------------------------------------
// Copyright 2006-2009, GeoTelematic Solutions, Inc.
// All rights reserved
// ----------------------------------------------------------------------------
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// 
// http://www.apache.org/licenses/LICENSE-2.0
// 
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// ----------------------------------------------------------------------------
// Change History:
//  2007/01/25  Martin D. Flynn
//     -Initial release
//  2007/02/26  Martin D. Flynn
//     -Change made to display device delection pull-down, event if there is only 
//      one device available.
//  2007/06/03  Martin D. Flynn
//     -Added I18N support
//  2007/06/13  Martin D. Flynn
//     -Added support for browsers with disabled cookies
//     -Fixed IE map update URL caching problem. (IE would cache the map data update
//      url, which would not properly update the map when a new device was selected.)
//  2007/06/30  Martin D. Flynn
//     -Changed to use User timezone, when available.
//  2007/07/27  Martin D. Flynn
//     -Added 'getNavigationTab(...)'
//  2007/11/28  Martin D. Flynn
//     -Added support for a 'fleet' (device groups) based map
//  2008/02/17  Martin D. Flynn
//     -Added support for map auto-update.
//  2008/02/21  Martin D. Flynn
//     -Changed used of "escape(X)" to "strEncode(X)" [defined in 'utils.js' as a call 
//      to "encodeURIComponent(X)"] which fixes the encoding of "GMT+X:XX" timezones 
//      (previously "GMT+1:00" would be encoded as "GMT 1:00", which ended up being 
//      interpreted as just "GMT").
//  2008/04/11  Martin D. Flynn
//     -Use the account/user timezone when calculating the "default" date range.
//  2008/08/15  Martin D. Flynn
//     -Added initial support for device 'ping' (not yet fully supported)
//  2008/08/17  Martin D. Flynn
//     -Added support for collapsible calendars.
//     -Added "Distance" title line (below "Cursor Location")
//     -Both "Update Map" and "Auto Update" buttons can be displayed at the same time.
//     -Supporting Javascript moved to 'TrackMap.js'
//  2008/08/20  Martin D. Flynn
//     -Fixed default From/To time to default to beginning/ending of the current day.
//  2008/08/24  Martin D. Flynn
//     -Added ability to change "Ping" button title via AccountString.
//     -Added 'Replay' support.
//  2008/09/19  Martin D. Flynn
//     -Added support for updating map with "Last" point.
//     -Added support for starting "AutoUpdate" on map load.
//  2008/12/01  Martin D. Flynn
//     -Added Device Link display option
//  2009/09/23  Martin D. Flynn
//     -Sort combo-box Device/Groups by the description
//     -Added support for displaying 'From' calandar on Fleet maps
//  2009/10/02  Martin D. Flynn
//     -Added support for displaying sorting device/group selection by id, name,
//      or description.
//  2009/11/01  Martin D. Flynn
//     -Escape html characters in displayed form values.
//  2009/11/10  Martin D. Flynn
//     -Fix: ignore "trackMap.mapUpdateOnLoad" property when displaying fleet map.
// ----------------------------------------------------------------------------
package org.opengts.war.track.page;

/* explicit imports required (due to conflict with "Calendar") */
import java.util.Locale;
import java.util.TimeZone;
import java.util.Iterator;
import java.util.Vector;
import java.util.Map;
import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.opengts.util.*;
import org.opengts.dbtools.*;
import org.opengts.db.*;
import org.opengts.db.tables.*;

import org.opengts.war.tools.*;
import org.opengts.war.track.Calendar;
import org.opengts.war.track.*;
import org.opengts.war.maps.JSMap;
import org.opengts.war.report.ReportPresentation;

public abstract class TrackMap
    extends WebPageAdaptor
    implements Constants
{

    // ------------------------------------------------------------------------

    private static final String  ID_DEVICE_ID                   = "deviceSelector";
    private static final String  ID_DEVICE_DESCR                = "deviceDescription";

    // ------------------------------------------------------------------------

    public  static final String  _ACL_AUTO                      = "auto";
    private static final String  _ACL_LIST[]                    = new String[] { _ACL_AUTO };

    // ------------------------------------------------------------------------
    // Properties

    public static final String   PROP_statusCodes               = "statusCodes";
    public static final String   PROP_showFleetFromCalendar     = "showFleetFromCalendar";
    public static final String   PROP_fleetDeviceEventCount     = "fleetDeviceEventCount";
    public static final String   PROP_mapTypeTitle              = "mapTypeTitle";
    
    public static final String   PROP_autoUpdate_enable         = "autoUpdate.enable";
    public static final String   PROP_autoUpdate_onload         = "autoUpdate.onload";
    public static final String   PROP_autoUpdate_interval       = "autoUpdate.interval";
    public static final String   PROP_autoUpdate_count          = "autoUpdate.count";

    // ------------------------------------------------------------------------
    // forms

    public  static final String  FORM_SELECT_DEVICE             = "SelectDeviceForm";
    public  static final String  FORM_PING_DEVICE               = "PingDeviceForm";

    // ------------------------------------------------------------------------
    // Commands

    public  static final String  COMMAND_DEVICE_PING            = "devping";                // arg=<N/A>
    public  static final String  COMMAND_MAP_UPDATE             = "mapupd";                 // arg=<N/A>
    public  static final String  COMMAND_KML_UPDATE             = "kmlupd";                 // arg=<N/A>
    public  static final String  COMMAND_AUTO_UPDATE            = "auto";                   // arg=interval,maxcount

    // ------------------------------------------------------------------------
    // Calendar vars

    public  static final String  CALENDAR_FROM                  = "mapCal_fr";
    public  static final String  CALENDAR_TO                    = "mapCal_to";
    
    // ------------------------------------------------------------------------
    // Auto update map timer

    private static final boolean DFT_AUTO_ENABLED               = false;
    private static final long    DFT_AUTO_DURATION              = DateTime.MinuteSeconds(20);
    private static final long    DFT_AUTO_INTERVAL              = DateTime.MinuteSeconds(1);
    private static final long    DFT_AUTO_MAXCOUNT              = DFT_AUTO_DURATION / DFT_AUTO_INTERVAL;

    private static final String  ID_MAP_AUTOUPDATE_BTN          = "mapAutoUpdateButton";
    private static final String  ID_MAP_UPDATE_BTN              = "mapUpdateButton";
    private static final String  ID_MAP_LAST_BTN                = "mapLastButton";
    private static final String  ID_MAP_REPLAY_BTN              = "mapReplayButton";
    private static final String  ID_MAP_SHOW_INFO               = "mapShowInfoBox";
    private static final String  ID_PING_DEVICE_BTN             = "pingDeviceButton";

    // ------------------------------------------------------------------------
    // property values

    // PrivateLabel.PROP_TrackMap_mapUpdateOnLoad
    private static final String MAP_UPDATE_ALL[]     = new String[] { "all"  , "true"  };
    private static final String MAP_UPDATE_LAST[]    = new String[] { "last" , "false" };

    // PrivateLabel.PROP_TrackMap_autoUpdateRecenter
    private static final String AUTO_RECENTER_NONE[] = new String[] { "no"  , "0", "false", "none" };
    private static final String AUTO_RECENTER_LAST[] = new String[] { "last", "1"                  };
    private static final String AUTO_RECENTER_ZOOM[] = new String[] { "zoom", "2", "true" , "yes"  };

    // PrivateLabel.PROP_TrackMap_showLocateNow
    private static final String SHOW_PING_FALSE[]    = new String[] { "false" , "no"  };
    private static final String SHOW_PING_TRUE[]     = new String[] { "true"  , "yes" };
    private static final String SHOW_PING_DEVICE[]   = new String[] { "device"        };

    // PrivateLabel.PROP_TrackMap_calendarDateOnLoad
    private static final String CALENDAR_DATE_NOW[]  = new String[] { "current", "now"    };
    private static final String CALENDAR_DATE_LAST[] = new String[] { "last"   , "device" };

    // PrivateLabel.PROP_TrackMap_mapControlLocation
    private static final String CONTROLS_ON_LEFT[]   = new String[] { "left", "true" };

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // WebPage interface

    private boolean         isFleet = false;
    private int             statusCodes[] = null;
    private boolean         showFromCalendar = false;

    public TrackMap()
    {
        super();
    }
    
    protected void postInit()
    {
        super.postInit();
        
        /* status codes */
        this.statusCodes = this.getProperties().getIntArray(PROP_statusCodes,null);
        /** /
        if (!ListTools.isEmpty(this.statusCodes)) {
            for (int i = 0; i < this.statusCodes.length; i++) {
                Print.logInfo("Map StatusCode: 0x" + StringTools.toHexString(this.statusCodes[i],16));
            }
        }
        / **/
        
        /* Fleet: showFleetFromCalendar */
        if (this.isFleet()) {
            // fleet map
            String frCal = this.getProperties().getString(PROP_showFleetFromCalendar,"");
            this.showFromCalendar = (StringTools.isBlank(frCal) || frCal.equalsIgnoreCase("default"))?
                false : StringTools.parseBoolean(frCal,false);
        } else {
            // device map
            this.showFromCalendar = true;
        }

    }

    // ------------------------------------------------------------------------
    
    public String[] getChildAclList()
    {
        return _ACL_LIST;
    }

    // ------------------------------------------------------------------------

    protected void setFleet(boolean fleet)
    {
        this.isFleet = fleet;
        this.showFromCalendar = !this.isFleet;
    }
    
    public boolean isFleet()
    {
        return this.isFleet;
    }

    // ------------------------------------------------------------------------

    protected int[] getStatusCodes()
    {
        return this.statusCodes;
    }
    
    // ------------------------------------------------------------------------
    // GPS/Map JavaScript

    protected void writeJS_MapUpdate(
        final RequestProperties reqState, 
        PrintWriter out,
        String  mapUpdURL,
        String  devicePingURL,
        String  kmlUpdURL,
        boolean autoUpdateEnabled,
        boolean autoUpdateOnLoad,
        long    autoInterval,
        long    autoMaxCount
        )
        throws IOException
    {
        // external JavaScript functions:
        //   - mapDevicePing(pingURL);
        //   - mapProviderParseXML(mapEventRecords)
        //   - mapProviderUpdateMap(mapDataURL,recenterMode,replay)
        //   - mapProviderUnload()
        //   - mapProviderToggleDetails()
        final boolean isFleet    = this.isFleet();
        String        parmDevGrp = isFleet? PARM_GROUP : PARM_DEVICE;
        PrivateLabel  privLabel  = reqState.getPrivateLabel();
        I18N          i18n       = privLabel.getI18N(TrackMap.class);

        /* start JavaScript */
        JavaScriptTools.writeStartJavaScript(out);

        /* Calendar attributes */
        boolean calFade     = false;
        boolean calCollapse = false;
        boolean calDivBox   = false;
        switch (Calendar.getCalendarAction(privLabel.getStringProperty(PrivateLabel.PROP_TrackMap_calendarAction,null))) {
            case FIXED :
                calCollapse = false;
                calFade     = false;
                calDivBox   = false;
                break;
            case FADE  :
                calCollapse = true;
                calFade     = true;
                calDivBox   = false;
                break;
            case SWITCH:
                calCollapse = true;
                calFade     = false;
                calDivBox   = false;
                break;
            case POPUP :
                calCollapse = false;
                calFade     = false;
                calDivBox   = true;
        }
        out.write("// TrackMap Calendar attributes\n");
        JavaScriptTools.writeJSVar(out, "CalendarCollapsible"       , calCollapse);
        JavaScriptTools.writeJSVar(out, "CalendarFade"              , calFade);
        JavaScriptTools.writeJSVar(out, "CalendarDivBox"            , calDivBox);

        /* Calendar OnLoad */
        String  calDateOnLoad = privLabel.getStringProperty(PrivateLabel.PROP_TrackMap_calendarDateOnLoad,CALENDAR_DATE_NOW[0]).toLowerCase();
        JavaScriptTools.writeJSVar(out, "CalendarDateOnLoad"        , calDateOnLoad);

        /* points to display OnLoad or when AutoUpdate is clicked */
        String mapUpdateOnLoad;
        if (isFleet) {
            // all devices if in fleet mode
            mapUpdateOnLoad = MAP_UPDATE_ALL[0];
        } else {
            // last/all if in device mode
            String muol = privLabel.getStringProperty(PrivateLabel.PROP_TrackMap_mapUpdateOnLoad,"");
            mapUpdateOnLoad = ListTools.containsIgnoreCase(MAP_UPDATE_LAST,muol)? MAP_UPDATE_LAST[0] : MAP_UPDATE_ALL[0];
        }
        
        /* auto-update attributes */
        int autoUpdateRecenterMode = 0;
        if (autoUpdateEnabled) {
            String mode = privLabel.getStringProperty(PrivateLabel.PROP_TrackMap_autoUpdateRecenter,AUTO_RECENTER_ZOOM[0]);
            if (ListTools.containsIgnoreCase(AUTO_RECENTER_NONE,mode)) {
                autoUpdateRecenterMode = 0;
            } else
            if (ListTools.containsIgnoreCase(AUTO_RECENTER_LAST,mode)) {
                autoUpdateRecenterMode = 1;
            } else {
                autoUpdateRecenterMode = 2;
            }
        }
        
        /* write map attributes */
        out.write("// TrackMap Update/AutoUpdate/Replay attributes\n");
        JavaScriptTools.writeJSVar(out, "MapUpdateOnLoad"           , mapUpdateOnLoad);
        JavaScriptTools.writeJSVar(out, "AutoUpdateEnable"          , autoUpdateEnabled);
        JavaScriptTools.writeJSVar(out, "AutoUpdateOnLoad"          , autoUpdateOnLoad);
        JavaScriptTools.writeJSVar(out, "AutoMaxCount"              , autoMaxCount);
        JavaScriptTools.writeJSVar(out, "AutoInterval"              , autoInterval);
        JavaScriptTools.writeJSVar(out, "AutoUpdateRecenterMode"    , autoUpdateRecenterMode);
        JavaScriptTools.writeJSVar(out, "AutoUpdateMapTimer"        , null);
        JavaScriptTools.writeJSVar(out, "AutoIntervalCount"         , 0);
        JavaScriptTools.writeJSVar(out, "AutoUpdateMapCount"        , 0);
        JavaScriptTools.writeJSVar(out, "LimitType"                 , privLabel.getStringProperty(PrivateLabel.PROP_TrackMap_limitType,"last"));
        JavaScriptTools.writeJSVar(out, "ID_MAP_UPDATE_BTN"         , ID_MAP_UPDATE_BTN);
        JavaScriptTools.writeJSVar(out, "ID_MAP_AUTOUPDATE_BTN"     , ID_MAP_AUTOUPDATE_BTN);
        JavaScriptTools.writeJSVar(out, "ID_MAP_REPLAY_BTN"         , ID_MAP_REPLAY_BTN);

        /* Localized text */
        out.write("// TrackMap localized text\n");
        JavaScriptTools.writeJSVar(out, "TEXT_autoUpdateStart"      , i18n.getString("TrackMap.startAutoUpdate","Auto"));
        JavaScriptTools.writeJSVar(out, "TEXT_autoUpdateStop"       , i18n.getString("TrackMap.stopAutoUpdate","Stop"));

        /* other vars */
        out.write("// TrackMap misc vars\n");
        JavaScriptTools.writeJSVar(out, "IS_FLEET"                  , isFleet);
        JavaScriptTools.writeJSVar(out, "IS_DEVICE"                 , !isFleet);
        JavaScriptTools.writeJSVar(out, "MAP_UPDATE_URL"            , mapUpdURL);
        JavaScriptTools.writeJSVar(out, "DEVICE_PING_URL"           , devicePingURL);
        JavaScriptTools.writeJSVar(out, "KML_UPDATE_URL"            , kmlUpdURL);
        JavaScriptTools.writeJSVar(out, "PARM_RANGE_FR"             , Calendar.PARM_RANGE_FR);
        JavaScriptTools.writeJSVar(out, "PARM_RANGE_TO"             , Calendar.PARM_RANGE_TO);
        JavaScriptTools.writeJSVar(out, "PARM_TIMEZONE"             , Calendar.PARM_TIMEZONE);
        JavaScriptTools.writeJSVar(out, "PARM_LIMIT"                , PARM_LIMIT);
        JavaScriptTools.writeJSVar(out, "PARM_LIMIT_TYPE"           , PARM_LIMIT_TYPE);
        JavaScriptTools.writeJSVar(out, "PARM_DEVICE_GROUP"         , parmDevGrp);
        JavaScriptTools.writeJSVar(out, "PARM_DEVICE_COMMAND"       , PARM_DEVICE_COMMAND);

        /* MapShapes (ZoomRegionShapes) */
        final Map<String,MapShape> mapShapes = reqState.getZoomRegionShapes();
        if (!ListTools.isEmpty(mapShapes)) {
            out.write("// MapShapes (ZoomRegions)\n");
            out.write("var trackZoomRegionShapes = new Array(\n");
            for (Iterator<MapShape> msi = mapShapes.values().iterator(); msi.hasNext();) {
                MapShape ms = msi.next();
                String N = ms.getName();
                String T = ms.getType().toString();
                long   R = Math.round(ms.getRadiusMeters());
                String P = ms.getPointsString();
                String C = ms.getColorString();
                out.write("  {");
                out.write(" name:\""   + N + "\",");
                out.write(" type:\""   + T + "\",");
                out.write(" radius:\"" + R + "\",");
                out.write(" points:\"" + P + "\",");
                out.write(" color:\""  + C + "\"");
                out.write(" }");
                if (msi.hasNext()) { out.write(","); }
                out.write("\n");
            }
            out.write(");\n");
        }

        /* device links */
        /*
        if (isFleet) {
            // TODO:
        } else {
            Device device = reqState.getSelectedDevice();
            if (device != null) {
                JavaScriptTools.writeJSVar(out, "DeviceLinkURL"         , device.getLinkURL());
                JavaScriptTools.writeJSVar(out, "DeviceLinkDescription" , device.getLinkDescription());
            } else {
                JavaScriptTools.writeJSVar(out, "DeviceLinkURL"         , null);
                JavaScriptTools.writeJSVar(out, "DeviceLinkDescription" , null);
            }
        }
        */

        /* Group/Device list */
        if (DeviceChooser.isDeviceChooserUseTable(privLabel)) {
            //DeviceChooser.writeDeviceList(out, reqState, "TrackSelectorList");
        }

        /* From Calendar */
        out.write("// Calendar vars \n");
        if (this.showFromCalendar) {
            //Print.logInfo("Writing 'From' Calendar JavaScript var ...");
            Calendar.writeNewCalendar(out, CALENDAR_FROM, null, i18n.getString("TrackMap.dateFrom","From"), reqState.getEventDateFrom()); 
        } else {
            JavaScriptTools.writeJSVar(out, CALENDAR_FROM, null);
        }
        Calendar.writeNewCalendar(out, CALENDAR_TO, null, i18n.getString("TrackMap.dateTo","To"), reqState.getEventDateTo());

        /* end JavaScript */
        JavaScriptTools.writeEndJavaScript(out);

        /* TrackMap.js */
        JavaScriptTools.writeJSInclude(out, JavaScriptTools.qualifyJSFileRef("TrackMap.js"));

        /* sorttable.js */
        if (DeviceChooser.isDeviceChooserUseTable(privLabel)) {
            JavaScriptTools.writeJSInclude(out, JavaScriptTools.qualifyJSFileRef(ReportPresentation.SORTTABLE_JS));
        }
        
    }

    // ------------------------------------------------------------------------

    public void writePage(
        final RequestProperties reqState, 
        String pageMsg)
        throws IOException
    {
        final PrivateLabel privLabel = reqState.getPrivateLabel();
        final I18N    i18n           = privLabel.getI18N(TrackMap.class);
        final Locale  locale         = reqState.getLocale();
        final String  devTitles[]    = reqState.getDeviceTitles();
        final String  grpTitles[]    = reqState.getDeviceGroupTitles();
        final Account currAcct       = reqState.getCurrentAccount(); // guaranteed, since login is required
        final User    currUser       = reqState.getCurrentUser();    // may be null
        String m = pageMsg;

        HttpServletRequest request = reqState.getHttpServletRequest();
        String  rangeFr  = (String)AttributeTools.getRequestAttribute(request, Calendar.PARM_RANGE_FR, "");
        String  rangeTo  = (String)AttributeTools.getRequestAttribute(request, Calendar.PARM_RANGE_TO, "");
        String  tzStr    = (String)AttributeTools.getRequestAttribute(request, Calendar.PARM_TIMEZONE, "");
        String  cmdName  = reqState.getCommandName();
        String  cmdArg   = reqState.getCommandArg();

        /* limit info */
        long   limitCnt  = AttributeTools.getRequestLong(request, PARM_LIMIT, -1L);
        String limitType = AttributeTools.getRequestString(request, PARM_LIMIT_TYPE, "");

        /* set "fleet" request type */
        final boolean isFleet = this.isFleet();
        reqState.setFleet(isFleet);

        /* no defined Device? */
        final Device device;
        if (isFleet) {
            device = null;
        } else {
            device = reqState.getSelectedDevice();
            if (device == null) {
                String devID = reqState.getSelectedDeviceID();
                if (StringTools.isBlank(devID)) {
                    m = i18n.getString("TrackMap.noDevices","There are currently no defined/authorized devices for this account.");
                    //Track.writeErrorResponse(reqState, m);
                    //return;
                } else {
                    m = i18n.getString("TrackMap.invalidDevices","Specified device ''{0}'' does not exist, or is invalid.", devID);
                }
            }
        }

        /* device "Ping" */
        final Map<String,String> commandMap;
        final boolean deviceSupportsPing;
        if (isFleet) {
            // no "ping" for fleet
            commandMap = null;
            deviceSupportsPing = false;
        } else 
        if ((device != null) && (device.getDCServerConfig() != null)) {
            // DCServerConfig control pings
            commandMap = device.getSupportedCommands(privLabel,currUser,"map");
            deviceSupportsPing = !ListTools.isEmpty(commandMap); // command access control by ACLs
        } else {
            // check for other device "ping"
            commandMap = null;
            String showLocateNow = privLabel.getStringProperty(PrivateLabel.PROP_TrackMap_showLocateNow,"device");
            if (ListTools.containsIgnoreCase(SHOW_PING_FALSE,showLocateNow)) {
                // explicit "false"
                deviceSupportsPing = false;
            } else
            if (ListTools.containsIgnoreCase(SHOW_PING_TRUE,showLocateNow)) {
                // explicit "true"
                deviceSupportsPing = true;
            } else {
                // check device
                deviceSupportsPing = (device != null)? device.isPingSupported(privLabel,currUser) : false;
            }
        }

        /* device link */
        final boolean showDeviceLink = !isFleet && privLabel.getBooleanProperty(PrivateLabel.PROP_TrackMap_showDeviceLink,false);

        /* page links */
        final String PageLinks[] = StringTools.split(privLabel.getStringProperty(PrivateLabel.PROP_TrackMap_pageLinks,null),',');
        final boolean includePageLinks = (PageLinks != null) && (PageLinks.length > 0);
        
        /* Google KML [COMMAND_KML_UPDATE] */
        final boolean includeGoogleKML;
        final String  googleKmlArg;
        String _googleKmlArg = privLabel.getStringProperty(PrivateLabel.PROP_TrackMap_showGoogleKML,null);
        if (_googleKmlArg == null) {
            includeGoogleKML = false;
            googleKmlArg     = null;
        } else
        if (_googleKmlArg.equalsIgnoreCase("last")) {
            includeGoogleKML = true;
            googleKmlArg     = "last";
        } else {
            includeGoogleKML = StringTools.parseBoolean(_googleKmlArg, false);
            googleKmlArg     = null;
        }

        /* TimeZone */
        if (StringTools.isBlank(tzStr)) {
            if (currUser != null) {
                // try User timezone
                tzStr = currUser.getTimeZone(); // may be blank
                if (StringTools.isBlank(tzStr) || tzStr.equals(User.DEFAULT_TIMEZONE)) {
                    // override with Account timezone
                    tzStr = currAcct.getTimeZone();
                }
            } else {
                // get Account timezone
                tzStr = currAcct.getTimeZone();
            }
            if (StringTools.isBlank(tzStr)) {
                // make sure we have a timezone 
                // (unecessary, since Account/User will return a timezone)
                tzStr = Account.DEFAULT_TIMEZONE;
            }
        }
        TimeZone tz = DateTime.getTimeZone(tzStr); // will be GMT if invalid
        AttributeTools.setSessionAttribute(request, Calendar.PARM_TIMEZONE, tzStr);
        reqState.setTimeZone(tz, tzStr);
        DateTime now = new DateTime(tz);
        //Print.logInfo("TrackMap TimeZone = [" + tzStr + "] " + tzStr);
        //Print.logInfo("Actual   TimeZone = [" + reqState.getTimeZoneString(null) + "] " + reqState.getTimeZone().getDisplayName());

        /* range 'from' [keywords: frDate, startDate, dateRange] */
        // "YYYY/MM[/DD[/hh[:mm[:ss]]]]"  ie "YYYY/MM/DD/hh:mm:ss"
        DateTime dateFr; // initialized below
        String rangeFrFld[] = !StringTools.isBlank(rangeFr)? StringTools.parseString(rangeFr, "/:") : null;
        if (!this.showFromCalendar) {
            dateFr = null;
        } else
        if (ListTools.isEmpty(rangeFrFld)) {
            if (isFleet) {
                // one month ago
                // (only save if displaying the 'From' Calendar
                dateFr = new DateTime(now.getMonthDelta(tz,-1), tz);
            } else {
                // beginning of today
                dateFr = new DateTime(now.getDayStart(tz), tz);
            }
        } else
        if (rangeFrFld.length == 1) {
            // parse as 'Epoch' time
            long epoch = StringTools.parseLong(rangeFrFld[0], now.getTimeSec());
            dateFr = new DateTime(epoch, tz);
        } else {
            // (rangeFrFld.length >= 2)
            int YY = StringTools.parseInt(rangeFrFld[0], now.getYear());
            int MM = StringTools.parseInt(rangeFrFld[1], now.getMonth1());
            int DD;     // initialized below
            int hh = 0; // default to beginning of day
            int mm = 0;
            int ss = 0;
            if (rangeFrFld.length >= 3) {
                // at least YYYY/MM/DD provided
                DD = StringTools.parseInt(rangeFrFld[2], now.getDayOfMonth());
                if (rangeFrFld.length >= 4) { hh = StringTools.parseInt(rangeFrFld[3], hh); }
                if (rangeFrFld.length >= 5) { mm = StringTools.parseInt(rangeFrFld[4], mm); }
                if (rangeFrFld.length >= 6) { ss = StringTools.parseInt(rangeFrFld[5], ss); }
            } else {
                // only YYYY/MM provided
                DD = 1;
            }
            dateFr = new DateTime(tz, YY, MM, DD, hh, mm, ss);
            //Print.logInfo("Fr: YY="+YY+", MM="+MM+", DD="+DD+", hh="+hh+", mm="+mm+", ss="+ss);
        }

        /* range 'to'  [keywords: toDate, endDate, dateRange] */
        // "YYYY/MM[/DD[/hh[:mm[:ss]]]]"  ie "YYYY/MM/DD/hh:mm:ss"
        DateTime dateTo; // initialized below
        String rangeToFld[] = !StringTools.isBlank(rangeTo)? StringTools.parseString(rangeTo, "/:") : null;
        if (ListTools.isEmpty(rangeToFld)) {
            dateTo = new DateTime(now.getDayEnd(tz), tz);
        } else
        if (rangeToFld.length == 1) {
            // parse as 'Epoch' time
            long epoch = StringTools.parseLong(rangeToFld[0], now.getTimeSec());
            dateTo = new DateTime(epoch, tz);
        } else {
            // (rangeToFld.length >= 2)
            int YY = StringTools.parseInt(rangeToFld[0], now.getYear());
            int MM = StringTools.parseInt(rangeToFld[1], now.getMonth1());
            int DD;      // initialized below
            int hh = 23; // default to end of day
            int mm = 59;
            int ss = 59;
            if (rangeToFld.length >= 3) {
                // at least YYYY/MM/DD provided
                DD = StringTools.parseInt(rangeToFld[2], now.getDayOfMonth());
                if (rangeToFld.length >= 4) { hh = StringTools.parseInt(rangeToFld[3], hh); }
                if (rangeToFld.length >= 5) { mm = StringTools.parseInt(rangeToFld[4], mm); }
                if (rangeToFld.length >= 6) { ss = StringTools.parseInt(rangeToFld[5], ss); }
            } else {
                // only YYYY/MM provided
                DD = DateTime.getDaysInMonth(tz, MM, YY);
            }
            dateTo = new DateTime(tz, YY, MM, DD, hh, mm, ss);
            //Print.logInfo("To: YY="+YY+", MM="+MM+", DD="+DD+", hh="+hh+", mm="+mm+", ss="+ss);
        }

        /* save from/to dates */
        if ((dateFr != null) && dateFr.after(dateTo)) { 
            dateFr = dateTo; 
        }
        if (this.showFromCalendar) {
            reqState.setEventDateFrom(dateFr);
            AttributeTools.setSessionAttribute(request, Calendar.PARM_RANGE_FR, Calendar.formatArgDateTime(dateFr));
        } else {
            reqState.setEventDateFrom(null);
            AttributeTools.setSessionAttribute(request, Calendar.PARM_RANGE_FR, "");
        }
        reqState.setEventDateTo(dateTo);
        AttributeTools.setSessionAttribute(request, Calendar.PARM_RANGE_TO, Calendar.formatArgDateTime(dateTo));
        //Print.logInfo("Date Range: " + dateFr + " ==> " + dateTo);

        /* map provider */
        final MapProvider mapProvider = reqState.getMapProvider();
        if (mapProvider == null) {
            Track.writeErrorResponse(reqState, i18n.getString("TrackMap.noMapProvider","No Map Provider defined for this URL"));
            return;
        }

        /* map dimension */
        final MapDimension mapDim = mapProvider.getDimension();
        final boolean mapAutoSize = (mapDim.getHeight() < 0);

        /* event limit/type */
        long maxPushpins = mapProvider.getMaxPushpins(isFleet);
        if ((limitCnt <= 0L) || (limitCnt > maxPushpins)) {
            limitCnt = maxPushpins;
        }
        reqState.setEventLimit(limitCnt);
        reqState.setEventLimitType(limitType);

        /* last event from device */
        try {
            EventData evd[] = (!isFleet && (device != null))? device.getLatestEvents(1L,true) : null;
            if (!ListTools.isEmpty(evd)) {
                reqState.setLastEventTime(new DateTime(evd[0].getTimestamp()));
            }
        } catch (DBException dbe) {
            // ignore
        }

        /* KML Display */
        if (cmdName.equals(COMMAND_KML_UPDATE)) {
            HttpServletResponse response = reqState.getHttpServletResponse();
            PrintWriter out = response.getWriter();
            try {
                int statCodes[]    = this.getStatusCodes();
                long perDevLimit   = (!StringTools.isBlank(cmdArg) && cmdArg.equals("last"))? 1L : -1L;
                EventData evdata[] = reqState.getMapEvents(statCodes, perDevLimit); // [KML] does not return null
                GoogleKML.getInstance().writeEvents(out, evdata, privLabel);
            } catch (DBException dbe) {
                Print.logException("Error reading Events", dbe);
                CommonServlet.setResponseContentType(response, HTMLTools.CONTENT_TYPE_PLAIN);
                out.println("\nError reading Events");
            }
            return;
        }

        /* CSV MapUpdate data request (special case of 'Map') */
        // Page: csv:*, or *:csv
        if (cmdName.equals(COMMAND_MAP_UPDATE)) {
            // This is how the displayed map gets its data
            int statCodes[] = this.getStatusCodes();
            mapProvider.writeMapUpdate(reqState, statCodes);
            return;
        }

        /* Device Ping request (special case of 'Map') */
        if (cmdName.equals(COMMAND_DEVICE_PING)) {
            HttpServletResponse response = reqState.getHttpServletResponse();
            CommonServlet.setResponseContentType(response, HTMLTools.CONTENT_TYPE_PLAIN); // UTF-8?
            PrintWriter out = response.getWriter();
            if (!isFleet) {
                String pingType   = DCServerConfig.COMMAND_CONFIG;
                String pingName   = (String)AttributeTools.getRequestAttribute(request, PARM_DEVICE_COMMAND, ""); // session or query
                String pingArgs[] = null;
                if (device == null) {
                    // no device? (unlikely here, but we must check anyway)
                    Print.logError("Ping Error: device is null!");
                    out.println(Track.DATA_RESPONSE_PING_ERROR);
                } else
                if (!device.sendDeviceCommand(pingType,pingName,pingArgs)) {
                    // unable to send ping? (not supported, etc.)
                    Print.logError("Ping Failed: %s/%s", device.getAccountID(), device.getDeviceID());
                    out.println(Track.DATA_RESPONSE_PING_ERROR);
                } else {
                    // 'ping' successful
                    Print.logInfo("Device Ping: %s/%s", device.getAccountID(), device.getDeviceID());
                    try {
                        // save ping count information
                        device.save();
                    } catch (DBException dbe) {
                        Print.logException("Saving Device 'Ping' count", dbe);
                    }
                    out.println(Track.DATA_RESPONSE_PING_OK);
                }
            } else {
                Print.logInfo("Invalid device ping while viewing fleet map ...");
                out.println(Track.DATA_RESPONSE_ERROR);
            }
            return;
        }
        
        /* auto-update attributes */
        boolean autoUpdateOk = privLabel.hasReadAccess(currUser, this.getAclName(_ACL_AUTO));
        final boolean autoUpdateEnabled;
        final boolean autoUpdateOnLoad;
        final long    autoInterval;
        final long    autoMaxCount;
        long _autoIntrv = 0L;
        long _autoMaxCt = 0L;
        if (cmdName.equals(COMMAND_AUTO_UPDATE)) {
            Print.logInfo("Auto-Update: arg = " + cmdArg);
            long v[] = StringTools.parseLong(StringTools.split(cmdArg,','),0L);
            if ((v != null) && (v.length == 2)) {
                _autoIntrv = v[0];
                _autoMaxCt = (v[1] > 0L)? v[1] : 2L;
            }
        }
        if (!autoUpdateOk) {
            // not authorized
            autoUpdateEnabled   = false;
            autoUpdateOnLoad    = false;
            autoInterval        = 0L;
            autoMaxCount        = 0L;
        } else
        if (_autoIntrv > 0L) {
            // overridden 
            autoUpdateEnabled   = true;
            autoUpdateOnLoad    = true;
            autoInterval        = _autoIntrv;
            autoMaxCount        = _autoMaxCt;
        } else {
            // Map properties check
            RTProperties rtp = this.getProperties();
            autoUpdateEnabled   = rtp.getBoolean(PROP_autoUpdate_enable  , mapProvider.getAutoUpdateEnabled(isFleet));
            autoUpdateOnLoad    = rtp.getBoolean(PROP_autoUpdate_onload  , mapProvider.getAutoUpdateOnLoad(isFleet));
            autoInterval        = rtp.getLong(   PROP_autoUpdate_interval, mapProvider.getAutoUpdateInterval(isFleet));
            autoMaxCount        = rtp.getLong(   PROP_autoUpdate_count   , mapProvider.getAutoUpdateCount(isFleet));
        }
        
        /* MapShapes */
        final Map<String,String> zoomRegions;
        final Map<String,MapShape> mapShapes = reqState.getZoomRegionShapes();
        if (mapShapes != null) {
            zoomRegions = new OrderedMap<String,String>();
            for (MapShape ms : mapShapes.values()) {
                if (ms.isZoomTo()) {
                    //Print.logInfo("Adding ZoomRegion: " + ms.getName());
                    zoomRegions.put(ms.getName(), ms.getDescription());
                } else {
                    //Print.logInfo("Skipping ZoomRegion: " + ms.getName());
                }
            }
        } else {
            zoomRegions = null;
        }

        /* Map attributes */
        final boolean showTimezoneSelect      = privLabel.getBooleanProperty(PrivateLabel.PROP_TrackMap_showTimezoneSelection,true);
        final boolean replayEnable            = !isFleet && mapProvider.getReplayEnabled();
        final boolean showUpdateLast          = !isFleet && 
            mapProvider.isFeatureSupported(MapProvider.FEATURE_CENTER_ON_LAST) && 
            privLabel.getBooleanProperty(PrivateLabel.PROP_TrackMap_showUpdateLast,false);

        /* Style Sheets */
        HTMLOutput HTML_CSS = new HTMLOutput() {
            public void write(PrintWriter out) throws IOException {
                JSMap.writeStyle(out, reqState);
                Calendar.writeStyle(out, reqState);
                if (DeviceChooser.isDeviceChooserUseTable(privLabel)) {
                    DeviceChooser.writeStyle(out, reqState);
                }
            }
        };

        /* JavaScript */
        HTMLOutput HTML_JS = new HTMLOutput() {
            public void write(PrintWriter out) throws IOException {
                String pageName = TrackMap.this.getPageName();
                MenuBar.writeJavaScript(out, pageName, reqState);
                mapProvider.writeJavaScript(out, reqState);
                Calendar.writeJavaScript(out, reqState);
                if (DeviceChooser.isDeviceChooserUseTable(privLabel)) {
                    DeviceChooser.writeJavaScript(out, locale, privLabel,
                        privLabel.getWebPageURL(reqState, pageName, Track.COMMAND_DEVICE_LIST));
                }
                TrackMap.this.writeJS_MapUpdate(reqState, out, 
                    //EncodeMakeURL(reqState, Track.BASE_URI(), pageName, COMMAND_MAP_UPDATE ), 
                    privLabel.getWebPageURL(reqState, pageName, COMMAND_MAP_UPDATE ),
                    //EncodeMakeURL(reqState, Track.BASE_URI(), pageName, COMMAND_DEVICE_PING),
                    privLabel.getWebPageURL(reqState, pageName, COMMAND_DEVICE_PING),
                    EncodeMakeURL(reqState,Track.BASE_URI()+".kml",pageName,COMMAND_KML_UPDATE,googleKmlArg),
                    autoUpdateEnabled, autoUpdateOnLoad, autoInterval, autoMaxCount
                    );
            }
        };

        /* content */
        final boolean mapSupportsCursorLocation = 
            mapProvider.isFeatureSupported(MapProvider.FEATURE_LATLON_DISPLAY) &&
            privLabel.getBooleanProperty(PrivateLabel.PROP_TrackMap_showCursorLocation,true);
        final boolean mapSupportsDistanceRuler  = 
            mapProvider.isFeatureSupported(MapProvider.FEATURE_DISTANCE_RULER) &&
            privLabel.getBooleanProperty(PrivateLabel.PROP_TrackMap_showDistanceRuler,true);
        final boolean mapControlsOnLeft = 
            ListTools.containsIgnoreCase(CONTROLS_ON_LEFT,privLabel.getStringProperty(PrivateLabel.PROP_TrackMap_mapControlLocation,""));
        HTMLOutput HTML_CONTENT = new HTMLOutput((mapAutoSize? CommonServlet.CSS_CONTENT_MAP_FULL : CommonServlet.CSS_CONTENT_MAP), m) {
            public void write(PrintWriter out) throws IOException {
                String pageName = TrackMap.this.getPageName();

                // Command Form
                // This entire form is 'hidden'.  It's used by JS functions to submit specific commands 
                String actionURL = Track.GetBaseURL(reqState); // EncodeMakeURL(reqState,Track.BASE_URI());
                out.println("\n<!-- Command form -->");
                out.println("<form id='"+FORM_COMMAND+"' name='"+FORM_COMMAND+"' method='post' action=\""+actionURL+"\" target='_top'>");
                out.println("  <input type='hidden' name='"+PARM_PAGE              +"' value=''/>");
                out.println("  <input type='hidden' name='"+PARM_COMMAND           +"' value=''/>");
                out.println("  <input type='hidden' name='"+PARM_ARGUMENT          +"' value=''/>");
                out.println("  <input type='hidden' name='"+Calendar.PARM_RANGE_FR +"' value=''/>");
                out.println("  <input type='hidden' name='"+Calendar.PARM_RANGE_TO +"' value=''/>");
                out.println("  <input type='hidden' name='"+Calendar.PARM_TIMEZONE +"' value=''/>");
                out.println("</form>");
                out.println("\n");

                // start of map/date table (2 columns)
                String tableStyle = "width:100%;" + (mapAutoSize?" height:100%;":"");
                out.println("<table border='0' cellspacing='0' cellpadding='0' style='"+tableStyle+"'>"); // [

                // Account/Device header
                out.println("\n<!-- Device/Group selection row -->");
                out.println("<tr>");
                out.println("<td colspan='2' style='width:100%; height:16px; padding:0px 0px 5px 0px;'>");
                out.println("<table border='0'  cellspacing='0' cellpadding='0' style='width:100%;'><tbody><tr>");

                out.println("<td align='left' style='width:100%; font-size:9pt; height:19px;'>");
                out.println("<form id='"+FORM_SELECT_DEVICE+"' name='"+FORM_SELECT_DEVICE+"' method='post' target='_top'>");
                out.println("<input type='hidden' name='"+PARM_PAGE              +"' value='" + FilterValue(pageName) + "'/>");
                out.println("<input type='hidden' name='"+Calendar.PARM_RANGE_FR +"' value=''/>");
                out.println("<input type='hidden' name='"+Calendar.PARM_RANGE_TO +"' value=''/>");
                out.println("<input type='hidden' name='"+Calendar.PARM_TIMEZONE +"' value=''/>");
                
                out.write("<table cellspacing='0' cellpadding='0' border='0'><tr>\n");
                String mapTypeTitle = TrackMap.this.getProperties().getString(PROP_mapTypeTitle,null);
                if (StringTools.isBlank(mapTypeTitle)) {
                    mapTypeTitle = isFleet?
                        i18n.getString("TrackMap.fleetMap" ,"{0} Map", grpTitles) :
                        i18n.getString("TrackMap.deviceMap","{0} Map", devTitles);
                }
                out.print("<td><b>"+mapTypeTitle+":</b>&nbsp;</td>");
                String selId = isFleet? reqState.getSelectedDeviceGroupID() : reqState.getSelectedDeviceID();
                String parmDevGrp = isFleet? PARM_GROUP : PARM_DEVICE;
                IDDescription.SortBy sortBy = DeviceChooser.getSortBy(privLabel);
                if (DeviceChooser.isDeviceChooserUseTable(privLabel)) {
                    out.write("<td>");
                    String chooserStyle   = "height:17px; padding:0px 0px 0px 3px; margin:0px 0px 0px 3px; cursor:pointer; border:1px solid gray;";
                    String chooserOnclick = "javascript:trackMapShowSelector()";
                    switch (sortBy) {
                        case DESCRIPTION : {
                            String selDesc = FilterValue(isFleet?reqState.getDeviceGroupDescription(selId,false):reqState.getDeviceDescription(selId,false));
                            out.write("<input id='"+ID_DEVICE_ID   +"' name='"+parmDevGrp     +"' type='hidden' value='"+selId+"'>");
                            out.write("<input id='"+ID_DEVICE_DESCR+"' name='"+ID_DEVICE_DESCR+"' type='text' value='"+selDesc+"' readonly size='20' style='"+chooserStyle+"' onclick=\""+chooserOnclick+"\">");
                            } break;
                        case NAME : {
                            String selName = FilterValue(isFleet?reqState.getDeviceGroupDescription(selId,true ):reqState.getDeviceDescription(selId,true ));
                            out.write("<input id='"+ID_DEVICE_ID   +"' name='"+parmDevGrp     +"' type='hidden' value='"+selId+"'>");
                            out.write("<input id='"+ID_DEVICE_DESCR+"' name='"+ID_DEVICE_DESCR+"' type='text' value='"+selName+"' readonly size='20' style='"+chooserStyle+"' onclick=\""+chooserOnclick+"\">");
                            } break;
                        case ID :
                        default : {
                            out.write("<input id='"+ID_DEVICE_ID   +"' name='"+parmDevGrp     +"' type='text' value='"+selId  +"' readonly size='14' style='"+chooserStyle+"' onclick=\""+chooserOnclick+"\">");
                            } break;
                    }
                    out.write("</td>");
                    out.write("<td><img src='images/Pulldown.png' height='17' style='cursor:pointer;' onclick='javascript:trackMapShowSelector()'></td>");
                    out.write("<td style='padding-left:6px;'>&nbsp;</td>");
                } else {
                    OrderedSet<String> dgList = isFleet? reqState.getDeviceGroupList(true) : reqState.getDeviceList();
                    if (ListTools.isEmpty(dgList)) {
                        // should not occur
                        String id     = DeviceGroup.DEVICE_GROUP_NONE;
                        String dgDesc = FilterValue("?");
                        out.write("<td>");
                        out.write("<input id='"+ID_DEVICE_ID   +"' name='"+parmDevGrp    +"' type='hidden' value='"+id+"'>");
                        out.write("<input id='"+ID_DEVICE_DESCR+"' name='"+ID_DEVICE_DESCR+"' class='"+CommonServlet.CSS_TEXT_READONLY+"' type='text' readonly size='16' maxlength='32' value='"+dgDesc+"'>");
                        out.write("</td>\n");
                    } else
                    if (DeviceChooser.showSingleItemTextField(privLabel) && (dgList.size() == 1)) {
                        String id = dgList.get(0);
                        out.write("<td>");
                        if (sortBy.equals(IDDescription.SortBy.ID)) {
                            out.write("<input id='"+ID_DEVICE_ID   +"' name='"+parmDevGrp     +"' class='"+CommonServlet.CSS_TEXT_READONLY+"' type='text' readonly size='16' maxlength='32' value='"+id+"'>");
                        } else {
                            boolean rtnDispName = sortBy.equals(IDDescription.SortBy.NAME);
                            String desc = FilterValue(isFleet?reqState.getDeviceGroupDescription(id,rtnDispName):reqState.getDeviceDescription(id,rtnDispName));
                            out.write("<input id='"+ID_DEVICE_ID   +"' name='"+parmDevGrp     +"' type='hidden' value='"+id+"'>");
                            out.write("<input id='"+ID_DEVICE_DESCR+"' name='"+ID_DEVICE_DESCR+"' class='"+CommonServlet.CSS_TEXT_READONLY+"' type='text' readonly size='16' maxlength='32' value='"+desc+"'>");
                        }
                        out.write("</td>\n");
                    } else {
                        // sort by description (id's are unique, but the description may not be)
                        java.util.List<IDDescription> sortList = new Vector<IDDescription>();
                        boolean rtnDispName = sortBy.equals(IDDescription.SortBy.NAME);
                        for (String id : dgList) {
                            String desc = isFleet? reqState.getDeviceGroupDescription(id,rtnDispName) : reqState.getDeviceDescription(id,rtnDispName);
                            sortList.add(new IDDescription(id,desc));
                        }
                        IDDescription.SortList(sortList, rtnDispName? IDDescription.SortBy.DESCRIPTION : sortBy);
                        out.print("<td>");
                        out.print("<select id='"+ID_DEVICE_ID+"' name='"+parmDevGrp+"' onchange=\"javascript:trackMapSelectDevice()\">");
                        for (IDDescription dd : sortList) {
                            String id   = dd.getID();
                            String desc = dd.getDescription();
                            String sel  = id.equals(selId)? "selected" : "";
                            String disp = FilterValue(sortBy.equals(IDDescription.SortBy.ID)?id:desc);
                            out.println("<option value='"+id+"' "+sel+">"+disp+"</option>");
                        }
                        out.write("</select>\n");
                        out.write("</td>\n");
                    }
                }
                if (!ListTools.isEmpty(zoomRegions)) { // EXPERIMENTAL
                    out.write("<td>");
                    out.write("<span style='padding-left:20px;'><b>"+i18n.getString("TrackMap.region" ,"Region") + ": </b></span>\n");
                    out.write("<select id='regionSelector' name='"+PARM_REGION+"' onchange=\"javascript:trackMapSelectRegion()\">\n");
                    boolean firstRegion = true;
                    for (String rid : zoomRegions.keySet()) {
                        String id   = FilterValue(rid);
                        String desc = FilterText(zoomRegions.get(rid));
                        String sel  = firstRegion? "selected" : "";
                        out.write("<option value='"+id+"' "+sel+">"+desc+"</option>\n");
                        firstRegion = false;
                    }
                    out.write("</select>\n");
                    out.write("</td>\n");
                }
                out.write("</tr></table>\n");
                
                out.write("</form>\n");
                out.write("</td>\n");
 
                // last GPS Event
                out.println("<td nowrap style='font-size:9pt'>");
                if (!isFleet) {
                    DateTime dt  = reqState.getLastEventTime();
                    TimeZone tz  = reqState.getTimeZone();
                    String _date = (dt != null)? dt.format(currAcct.getDateFormat(),tz) : "";
                    String _time = (dt != null)? dt.format(currAcct.getTimeFormat()) : i18n.getString("TrackMap.unavailable","unavailable");
                    String _tmz  = (dt != null)? dt.format("z",tz) : "";
                    out.print  ("<span style='width:100%; text-align:right;'>(");
                    out.print  (i18n.getString("TrackMap.lastGpsEvent","Last GPS event:") + "&nbsp;");
                    String dateTooltip = i18n.getString("TrackMap.lastGpsDate.tooltip", "Click to reset calendars to this date");
                    String dateOnclick = "javascript:trackMapGotoLastEventDate();";
                    String dateStyle   = "color: #0000CC; cursor: pointer;";
                    out.print  ("<span id='"+MapProvider.ID_LATEST_EVENT_DATE+"' onclick=\""+dateOnclick+"\" title='"+dateTooltip+"' style='"+dateStyle+"'>"+_date+"</span>&nbsp;");
                    out.print  ("<span id='"+MapProvider.ID_LATEST_EVENT_TIME+"'>"+_time+"</span>&nbsp;");
                    out.print  ("<span id='"+MapProvider.ID_LATEST_EVENT_TMZ +"'>"+_tmz +"</span>");
                    out.println(")</span>");
                }
                out.println("</td>");

                out.println("</tr></tbody></table>");
                out.println("</td>");
                out.println("</tr>");
    
                // start of map/calendar row
                out.println("<tr>");

                // Map cell on left, controls on right
                if (!mapControlsOnLeft) {
                    out.println("\n<!-- Map cell -->");
                    String mapCellStyle = "padding-right:5px; width:100%;" + (mapAutoSize?" height:100%;":"");
                    out.println("<td valign='top' style='"+mapCellStyle+"'>");
                    mapProvider.writeMapCell(out, reqState, null);
                    out.println("</td>\n");
                }

                // Date Range Selector
                //DateTime fr = reqState.getEventDateFrom();
                //DateTime to = reqState.getEventDateTo();
                //boolean sameMonth = (fr != null) && (fr.getYear() == to.getYear()) && (fr.getMonth1() == to.getMonth1());
                //String prevMoURL, nextMoURL;
                out.println("<td align='left' valign='top''>"); // style='height:100%;'>");
                out.println("<table border='0' cellspacing='0' cellpadding='0'>"); //  style='height:100%;'>"); // {

                // Calendars
                out.println("\n");
               if (TrackMap.this.showFromCalendar) {
                    out.println("<!-- 'From/To' Calendars -->");
                    out.println("<tr><td style='font-size:9pt; font-weight:bold; border-top: solid #CCCCCC 1px;'>"+i18n.getString("TrackMap.selectDates","Select Date Range:")+"</td></tr>");
                    out.println("<tr><td align='center' valign='top'>\n");
                    out.println("<div   id='"+Calendar.ID_CAL_DIV+"' class='"+Calendar.CLASS_CAL_DIV+"' style='margin-top: 3px;'>");
                    out.println(  "<div id='"+CALENDAR_FROM+"' style='width:100%;'></div>");
                    out.println(  "<div id='"+CALENDAR_TO  +"' style='width:100%; margin-top:4px;'></div>");
                    out.println(  "<div id='"+Calendar.ID_CAL_BOTTOM+"'></div>");
                    out.println("</div>\n");
                    out.println("</td></tr>\n");
                } else {
                    out.println("<!-- 'To' Calendar -->");
                    out.println("<tr><td style='font-size:9pt; font-weight:bold; border-top: solid #CCCCCC 1px;'>"+i18n.getString("TrackMap.selectToDate","Select 'To' Date:")+"</td></tr>");
                    out.println("<tr><td valign='top'>");
                    out.println("<div   id='"+Calendar.ID_CAL_DIV+"' class='"+Calendar.CLASS_CAL_DIV+"' style='margin-top: 3px;'>");
                    out.println(  "<div id='"+CALENDAR_TO+"' class='"+Calendar.CLASS_CAL_DIV+"'></div>");
                    out.println(  "<div id='"+Calendar.ID_CAL_BOTTOM+"'></div>");
                    out.println("</div>\n");
                    out.println("</td></tr>");
                }

                // Map update form
                out.println("<tr>");
                out.println("<td style='padding-top:2px;'>");

                // Timezone
                if (showTimezoneSelect) {
                    out.println("\n<!-- Timezone select -->");
                    out.println("<form id='TimeZoneSelect' name='TimeZoneSelect' method='get' action=\"javascript:true;\" target='_top'>");
                    out.println("<span style='font-size:8pt;'><b>"+i18n.getString("TrackMap.timeZone","TimeZone:")+"</b></span><br>");
                    out.println("<div style='height:18px;'>");
                    out.println("<select name='"+Calendar.PARM_TIMEZONE+"' onchange=\"javascript:calSelectTimeZone(document.TimeZoneSelect."+Calendar.PARM_TIMEZONE+".value)\">");
                    String timeZone = reqState.getTimeZoneString(null);
                    java.util.List _tzList = reqState.getTimeZonesList();
                    for (Iterator i = _tzList.iterator(); i.hasNext();) {
                        String tmz = (String)i.next();
                        String sel  = tmz.equals(timeZone)? "selected" : "";
                        out.println("  <option value='"+tmz+"' "+sel+">"+tmz+"</option>");
                    }
                    out.println("</select>");
                    out.println("</div>");
                    out.println("</form>");
                }

                out.println("<hr>");

                // "Update All"
                String i18nUpdateBtn = i18n.getString("TrackMap.updateAll","Update");
                String i18nUpdateTip = i18n.getString("TrackMap.updateAll.tooltip","Click to update map points");
                out.println("<form id='UpdateMap' name='UpdateMap' method='get' target='_top'>");
                out.println("<!-- 'Update All' -->");
                out.print  ("<input class='formButton' id='"+ID_MAP_UPDATE_BTN+"' type='button' name='update' value='"+i18nUpdateBtn+"' title=\""+i18nUpdateTip+"\" onclick=\"javascript:trackMapClickedUpdateAll();\">");
                if (showUpdateLast) {
                    String i18nLastBtn = i18n.getString("TrackMap.updateLast","Last");
                    String i18nLastTip = i18n.getString("TrackMap.updateLast.tooltip","Click to update last location");
                    out.println("<!-- 'Update Last' -->");
                    out.print  ("<input class='formButton' id='"+ID_MAP_LAST_BTN+"' type='button' name='update' value='"+i18nLastBtn+"' title=\""+i18nLastTip+"\" onclick=\"javascript:trackMapClickedUpdateLast();\">");
                }
                if (autoUpdateEnabled) {
                    String i18nAutoBtn = i18n.getString("TrackMap.startAutoUpdate","Auto");
                    String i18nAutoTip = i18n.getString("TrackMap.startAutoUpdate.tooltip","Click to start/stop auto-update");
                    out.println("<!-- 'Auto Update' -->");
                    out.print  ("<input class='formButton' id='"+ID_MAP_AUTOUPDATE_BTN+"' type='button' name='autoUpdate' value='"+i18nAutoBtn+"' title=\""+i18nAutoTip+"\" onclick=\"javascript:trackMapClickedAutoUpdate();\">");
                }
                out.println("</form>");

                // "Update Last"
                /*
                if (showUpdateLast) {
                    String i18nLastPoint = i18n.getString("TrackMap.updateLast","Last");
                    String i18nLastTip   = i18n.getString("TrackMap.updateLast.tooltip","Click to update last location");
                    out.println("<!-- 'Update Last' -->");
                    out.println("<form id='"+ID_CENTER_LAST_POINT_FORM+"' name='"+ID_CENTER_LAST_POINT_FORM+"' method='get' target='_top' style='margin-top:5px'>");
                    out.print  (  "<span title=\""+i18nLastTip+"\">");
                    out.print  (    "<label for='centerLastPoint'>"+i18nLastPoint+"</label>&nbsp;");
                    out.print  (        Form_CheckBox("centerLastPoint","centerLastPoint",true,false,null,"javascript:trackMapClickedUpdateLast()"));
                    out.print  (  "</span>");
                    out.println("</form>");
                }
                */

                // "Replay Map"
                if (replayEnable) {
                    String i18nReplayBtn = i18n.getString("TrackMap.replayMap","Replay");
                    String i18nReplayTip = i18n.getString("TrackMap.replayMap.tooltip","Click to start/pause map pushpin 'Replay'");
                    String i18nInfoText  = i18n.getString("TrackMap.showInfoBox","InfoBox");
                    String i18nInfoTip   = i18n.getString("TrackMap.showInfoBox.tooltip","Select to show Info-Box during replay"); 
                    out.println("<!-- 'Replay Map' -->");
                    out.println("<form id='ReplayMap' name='ReplayMap' method='get' action=\"javascript:trackMapClickedReplay(document.getElementById('ReplayMap')."+ID_MAP_SHOW_INFO+".checked);\" target='_top'>");
                    out.print  (  "<table cellpadding='0' cellspacing='0' border='0' style='margin-top: 2px;'><tr>"); // {
                    out.print  (    "<td valign='center' style='padding-right: 3px'><b>"+i18nReplayBtn+"</b></td>");
                    out.print  (    "<td valign='center'><input id='"+ID_MAP_REPLAY_BTN+"' type='image' name='replayMap' src='images/Play20.png' title=\""+i18nReplayTip+"\"></td>");
                    out.print  (    "<td valign='center' style='padding-left: 3px'>");
                    out.print  (       "<span title=\""+i18nInfoTip+"\">");
                    out.print  (         "<label for='"+ID_MAP_SHOW_INFO+"'>"+i18nInfoText+"</label>&nbsp;" + Form_CheckBox(ID_MAP_SHOW_INFO,ID_MAP_SHOW_INFO,true,false,null,null));
                    out.print  (       "</span>");
                    out.print  (    "</td>");
                    out.println(  "</tr></table>"); // }
                    out.println("</form>");
                }

                out.println("</td>");
                out.println("</tr>");

                out.println("\n<!-- Cursor Location / Distance Ruler -->");
                out.println("<tr>");
                out.println("<td valign='top'>");
                out.println("<hr>");
                if (mapSupportsCursorLocation || mapSupportsDistanceRuler) {
                    if (mapSupportsCursorLocation) {
                        out.println(" <b>"+i18n.getString("TrackMap.map.cursorLoc","Cursor Location")+":</b>");
                        out.println(" <div id='"+MapProvider.ID_LAT_LON_DISPLAY +"' style='margin-left:10px;'></div>");
                    }
                    if (mapSupportsDistanceRuler) {
                        out.println(" <b>"+i18n.getString("TrackMap.map.distance","Distance (ctrl-drag)")+":</b>");
                        out.println(" <div id='"+MapProvider.ID_DISTANCE_DISPLAY+"' style='margin-left:10px;'>0.00 "+reqState.getDistanceUnits().toString(locale)+"</div>");
                    }
                    out.println("<hr>");
                }
                out.println("</td>");
                out.println("</tr>");

                String legendTable = mapProvider.getIconSelectorLegend(reqState);
                if (!StringTools.isBlank(legendTable)) {
                    out.println("<!-- begin legend -->");
                    out.println("<tr>");
                    out.println("<td valign='top'>");
                    out.println(legendTable);
                    out.println("<hr>");
                    out.println("</td>");
                    out.println("</tr>");
                    out.println("<!-- end legend -->");
                }

                //out.println("<tr><td height='100%'>&nbsp;</td></tr>");

                out.println("<tr>");
                out.println("<td>");
                int accLine = 0;
                if (deviceSupportsPing) {
                    out.println("\n<!-- 'Locate Now' -->");
                    if (accLine > 0) { out.print("<br>"); }
                    if (!ListTools.isEmpty(commandMap)) {
                        String      sendText = i18n.getString("TrackMap.sendCommand","Send");
                        String      cmdID    = "DevCommand";
                        ComboMap    cmdMap   = new ComboMap(commandMap);
                        ComboOption cmdSel   = cmdMap.getFirstComboOption();
                        out.println("<form id='"+FORM_PING_DEVICE+"' name='"+FORM_PING_DEVICE+"' method='post' action=\"javascript:trackMapPingDevice(document.getElementById('"+cmdID+"').value);\" target='_top'>");
                        out.println("<div style='margin-bottom:4px;'>");
                        out.println(Form_ComboBox(cmdID, cmdID, true, cmdMap, cmdSel, null, -1));
                        out.println("<input id='"+ID_PING_DEVICE_BTN+"' type='submit' name='ping' value='" + FilterValue(sendText) + "'/>");
                        out.println("</div>");
                        out.println("</form>");
                    } else {
                        String sendText = null;
                        try {
                            AccountString as = AccountString.getAccountString(currAcct, AccountString.ID_PING_DEVICE);
                            if (as != null) {
                                // currently, 'isFleet' will always be false here
                                sendText = isFleet? as.getPluralTitle() : as.getSingularTitle();
                            }
                        } catch (DBException dbe) {
                            // ignore error
                        }
                        if (StringTools.isBlank(sendText)) {
                            sendText = i18n.getString("TrackMap.locateDevice","Locate {0}", devTitles);
                        }
                        out.println("<form id='"+FORM_PING_DEVICE+"' name='"+FORM_PING_DEVICE+"' method='post' action=\"javascript:trackMapPingDevice(null);\" target='_top'>");
                        out.println("<div style='margin-bottom:4px;'>");
                        out.println("<input id='"+ID_PING_DEVICE_BTN+"' type='submit' name='ping' value='" + FilterValue(sendText) + "'/>");
                        out.println("</div>");
                        out.println("</form>");
                    }
                    accLine++;
                }
                if (!isFleet && showDeviceLink && (device != null) && device.hasLink()) {
                    String url  = device.getLinkURL();
                    String desc = device.getLinkDescription();
                    if (StringTools.isBlank(desc)) {
                        desc = i18n.getString("TrackMap.link","Link"); // filter?
                    }
                    out.println("\n<!-- Device link -->");
                    if (accLine > 0) { out.print("<br>"); }
                    out.println("<a href='" + url + "' target='_blank'>" + desc + "</a>");
                    accLine++;
                }
                if (includePageLinks) {
                    // include page-links (a very limited-use feature).
                    out.println("\n<!-- Quick page links -->");
                    for (String pageLink : PageLinks) {
                        WebPage wp = privLabel.getWebPage(pageLink);
                        if (wp != null) {
                            if (accLine > 0) { out.print("<br>"); }
                            String pageURL = wp.encodePageURL(reqState);//, Track.BASE_URI());
                            out.println("<a href='" + pageURL + "' target='_top'>" + wp.getNavigationDescription(reqState) + "</a>");
                            accLine++;
                        } else {
                            // page not found
                        }
                    }
                }
                if (includeGoogleKML) {
                  //String kmlURL = EncodeMakeURL(reqState, Track.BASE_URI(), pageName, COMMAND_KML_UPDATE);
                    String kmlURL = privLabel.getWebPageURL(reqState, pageName, COMMAND_KML_UPDATE, googleKmlArg);
                    if (!StringTools.isBlank(kmlURL)) {
                        out.println("\n<!-- Google KML link -->");
                        if (accLine > 0) { out.print("<br>"); }
                        out.println("<span class='spanLink' onClick=\"javascript:trackMapUpdateKML()\">" + i18n.getString("TrackMap.googleKML","Google KML") + "</span>");
                        accLine++;
                    }
                }
                out.println("</td>");
                out.println("</tr>");

                out.println("<tr><td valign='top' style='height:100%;'>&nbsp;</td></tr>");

                out.println("</table>"); // }
                out.println("</td>"); // end of map control cell

                // Map cell on right, controls on left
                if (mapControlsOnLeft) {
                    out.println("\n<!-- Map cell -->");
                    String mapCellStyle = "padding-left:5px; width:100%;" + (mapAutoSize?" height:100%;":"");
                    out.println("<td valign='top' style='"+mapCellStyle+"'>");
                    mapProvider.writeMapCell(out, reqState, null);
                    out.println("</td>\n");
                }

                // end of 2nd row (Map + DateSelectors)
                out.println("</tr>");

                // 3rd row - border (span 2 columns)
                //out.println("<tr><td height='1' colspan='2' style='margin-top:1px;'></td></tr>");

                // 4th row - data table (span 2 columns)
                if (mapProvider.isFeatureSupported(MapProvider.FEATURE_DETAIL_REPORT)) {
                    out.write("\n<!-- Detail report -->\n");
                    out.write("<tr>\n");
                    out.write("<td colspan='2' align='center' style='padding-top:2px; border-top:1px solid #555555; font-size:9pt;'>\n");
                    out.write("<a class='trackMapDetailLocationControl' id='"+MapProvider.ID_DETAIL_CONTROL+"' href=\"javascript:mapProviderToggleDetails()\">");
                    out.write(i18n.getString("TrackMap.showLocationDetails","Show Location Details"));
                    out.write("</a>");
                    out.write("<div id='"+MapProvider.ID_DETAIL_TABLE+"' style='width:100%;'></div>\n");
                    out.write("</td>\n");
                    out.write("</tr>\n");
                }

                // end of map/selector table
                out.println("");
                out.println("</table>"); // ]

                /* write DeviceChooser DIV */
                if (DeviceChooser.isDeviceChooserUseTable(privLabel)) {
                    java.util.List<IDDescription> idList = reqState.createIDDescriptionList(isFleet, sortBy);
                    IDDescription list[] = idList.toArray(new IDDescription[idList.size()]);
                    DeviceChooser.writeChooserDIV(out, reqState, list, null);
                }

            }
        };

        /* write frame */
        CommonServlet.writePageFrame(
            reqState,
            "javascript:trackMapOnLoad();","javascript:trackMapOnUnload();",    // onLoad/onUnload
            HTML_CSS,                   // Style sheets
            HTML_JS,                    // Javascript
            null,                       // Navigation
            HTML_CONTENT);              // Content

    }

}