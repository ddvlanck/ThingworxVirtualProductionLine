package com.savaco.ConfigurationAgent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thingworx.communications.client.ConnectedThingClient;
import com.thingworx.communications.client.things.VirtualThing;
import com.thingworx.metadata.PropertyDefinition;
import com.thingworx.types.BaseTypes;
import com.thingworx.types.collections.AspectCollection;
import com.thingworx.types.constants.Aspects;
import com.thingworx.types.constants.DataChangeType;
import com.thingworx.types.primitives.BooleanPrimitive;
import com.thingworx.types.primitives.IntegerPrimitive;
import com.thingworx.types.primitives.NumberPrimitive;
import com.thingworx.types.primitives.StringPrimitive;
import java.util.List;
import java.util.Random;
import org.apache.commons.lang3.StringUtils;

public class AssetThing extends VirtualThing {
   
    public enum State { NOT_CONFIGURED, WARNING, RUNNING, PLANNED_DOWNTIME, UNPLANNED_DOWNTIME, UNAVAILABLE};

    private static final Logger LOG = LoggerFactory.getLogger(AssetThing.class);

    private final String name;
    private final ConnectedThingClient client;
    private final List<ThingProperty> device_Properties;
    private int prodRate;
    private int newProdRate;

    /**
     * @param name The name of the thing.
     * @param description A description of the thing.
     * @param client The client that this thing is associated with.
     * @param device_Properties
     * @throws java.lang.Exception
     */
    public AssetThing(String name, String description, ConnectedThingClient client, List<ThingProperty> device_Properties)
            throws Exception {

        super(name, description, client);
        this.name = name;
        this.client = client;

        this.device_Properties = device_Properties;

        for (int i = 0; i < this.device_Properties.size(); i++) {
            ThingProperty node = this.device_Properties.get(i);
            PropertyDefinition pd;
            AspectCollection aspects = new AspectCollection();

            if (StringUtils.isNumeric(node.getValue())) {
                pd = new PropertyDefinition(node.getPropertyName(), " ", BaseTypes.NUMBER);
            } else if ("true".equals(node.getValue()) || "false".equals(node.getValue())) {
                pd = new PropertyDefinition(node.getPropertyName(), " ", BaseTypes.BOOLEAN);
            } else {
                pd = new PropertyDefinition(node.getPropertyName(), " ", BaseTypes.STRING);
            }

            aspects.put(Aspects.ASPECT_DATACHANGETYPE, new StringPrimitive("VALUE"));
            aspects.put(Aspects.ASPECT_DATACHANGETHRESHOLD, new NumberPrimitive(0.0));
            aspects.put(Aspects.ASPECT_CACHETIME, new IntegerPrimitive(0));
            aspects.put(Aspects.ASPECT_ISPERSISTENT, new BooleanPrimitive(false));
            aspects.put(Aspects.ASPECT_ISREADONLY, new BooleanPrimitive(true));
            aspects.put("pushType", new StringPrimitive(DataChangeType.ALWAYS.name()));
            aspects.put(Aspects.ASPECT_ISLOGGED, new BooleanPrimitive(true));
            //aspects.put(Aspects.ASPECT_DEFAULTVALUE, new BooleanPrimitive(true));

            pd.setAspects(aspects);
            super.defineProperty(pd);

            if (node.getPropertyName().equals("ProductionRate")) {
                this.newProdRate = Integer.parseInt(node.getValue());
                this.prodRate = Integer.parseInt(node.getValue());
            }
        }
        super.initialize();
    }

    /*
    This method simulates new data for this thing based on the production rate set in the UI
    Even when there are no changes in production rate, temperature must vary.
    When there are changes, temperature, as well as failure rate has to change.
     */
    public void simulateNewData(int prodRateValue) {
        prodRate = newProdRate;
        newProdRate = prodRateValue;
        double temp = -1;
        double failure = -1;
        for (ThingProperty tp : this.getDevice_Properties()) {
            if (tp.getPropertyName().equals("ProductionRate")) {
                tp.setValue(Integer.toString(newProdRate));
            } else if (tp.getPropertyName().equals("Temperature")) {
                temp = Double.parseDouble(tp.getValue());
            } else if (tp.getPropertyName().equals("PercentageFailure")) {
                failure = Double.parseDouble(tp.getValue());
            }
        }

        int deltaProdRate = newProdRate - prodRate;
        int sign = 1;
        if (newProdRate < prodRate) {
            sign = -1;
        }
        if (temp != -1 && failure != -1 && deltaProdRate != 0) {
            double newTemp = temp + (Math.abs(deltaProdRate * 0.05) * sign);
            double newFailure = failure + (Math.abs(deltaProdRate * 0.025) * sign);
            newTemp = (double) Math.round(newTemp * 100d) / 100d;
            newFailure = (double) Math.round(newFailure * 100d) / 100d;
            try {
                for (ThingProperty tp : this.getDevice_Properties()) {
                    if (tp.getPropertyName().equals("Temperature")) {
                        tp.setValue("" + newTemp);
                    } else if (tp.getPropertyName().equals("PercentageFailure")) {
                        tp.setValue("" + newFailure);
                    }
                }
                this.setPropertyValue("ProductionRate", new IntegerPrimitive(newProdRate));
                this.setPropertyValue("Temperature", new NumberPrimitive(newTemp));
                this.setPropertyValue("PercentageFailure", new NumberPrimitive(newFailure));
                //LOG.info("TESTLOG ---- [" + this.getName() +  "] \tdeltaProdRate: " + deltaProdRate + "\ttemp:" + temp + "->" + newTemp + "\tFail:" + failure + "->" + newFailure);
            } catch (Exception e) {
                LOG.warn("TESTLOG ---- Exception setting remote properties. (AssetThing - simulateNewData)");
            }
        } else {
            //only randomize temp a little when there is no change in prodrate
            Random random = new Random();
            double newTemp = temp + (random.nextDouble() / 10 * temp * ( random.nextBoolean() ? 1 : -1 ));
            newTemp = (double) Math.round(newTemp * 100d) / 100d;
            try {
                this.setPropertyValue("Temperature", new NumberPrimitive(newTemp));
                for (ThingProperty tp : this.getDevice_Properties()) {
                    if (tp.getPropertyName().equals("Temperature")) {
                        tp.setValue("" + newTemp);
                    }
                }
                LOG.info("TESTLOG ---- [" + this.getName() +  "] \tdeltaProdRate: " + deltaProdRate + "\ttemp:" + temp + "->" + newTemp);
            } catch (Exception e) {
                LOG.warn("TESTLOG ---- Exception setting remote properties. (AssetThing - simulateNewData): " + this.getName());
                e.printStackTrace();
            }
        }
    }

    public List<ThingProperty> getDevice_Properties() {
        return device_Properties;
    }
    
    public ThingProperty getPropertyByName(String name){
        for(ThingProperty pt: device_Properties){
            if(pt.getPropertyName().equalsIgnoreCase(name)){
                return pt;
            }
        }
        return null;
    }
    
    public State convertToState(int numb){
        State status = null;
        switch(numb){
            case 0:  status = State.NOT_CONFIGURED;
                break;
            case 1: status = State.WARNING;
                break;
            case 2: status = State.RUNNING;
                break;
            case 3: status = State.PLANNED_DOWNTIME;
                break;
            case 4: status = State.UNPLANNED_DOWNTIME;
                break;
            case 5: status = State.UNAVAILABLE;
                break;
        }
        return status;
    }
}