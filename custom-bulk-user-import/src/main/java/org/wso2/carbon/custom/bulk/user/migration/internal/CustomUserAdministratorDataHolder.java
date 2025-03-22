package org.wso2.carbon.custom.bulk.user.migration.internal;

import org.wso2.carbon.context.CarbonCoreInitializedEvent;
import org.wso2.carbon.user.core.service.RealmService;

/**
 * Data holder class for the custom user administrator service.
 * This class is used to store and manage instances of required services
 * (e.g., RealmService and CarbonCoreInitializedEvent) that are injected via OSGi.
 */
public class CustomUserAdministratorDataHolder {

    private static CustomUserAdministratorDataHolder dataHolder = new CustomUserAdministratorDataHolder();
    private RealmService realmService;
    private CarbonCoreInitializedEvent carbonCoreInitializedEvent;

    public static CustomUserAdministratorDataHolder getInstance() {

        return dataHolder;
    }

    public void setDataHolder(CustomUserAdministratorDataHolder dataHolder) {

        this.dataHolder = dataHolder;
    }

    public RealmService getRealmService() {

        return realmService;
    }

    public void setRealmService(RealmService realmService) {

        this.realmService = realmService;
    }

    public CarbonCoreInitializedEvent getCarbonService() {

        return carbonCoreInitializedEvent;
    }

    public void setCarbonService(CarbonCoreInitializedEvent carbonCoreInitializedEvent) {

        this.carbonCoreInitializedEvent = carbonCoreInitializedEvent;
    }


}
