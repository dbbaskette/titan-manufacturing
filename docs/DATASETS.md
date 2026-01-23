# Titan Manufacturing — Public Datasets

Datasets for implementing the Titan 5.0 AI platform demo.

## 🎯 Recommended Datasets by Agent

| Agent | Dataset | Use Case at Titan |
|-------|---------|-------------------|
| **Sensor + Maintenance** | AI4I 2020 Predictive Maintenance | CNC machine sensor data, failure prediction |
| **Maintenance (RUL)** | NASA Turbofan C-MAPSS | Remaining useful life models |
| **Inventory + Orders** | DataCo SMART Supply Chain | 50K+ SKU management, B2B orders |
| **Logistics** | Smart Logistics Supply Chain | Global shipping optimization |
| **Governance** | Auto-catalog above datasets | OpenMetadata discovery |

---

## 📊 Manufacturing & Sensor Data

### AI4I 2020 Predictive Maintenance ⭐ TOP PICK
**Perfect for simulating the Phoenix CNC-007 incident**

- **URL**: https://www.kaggle.com/datasets/stephanmatzka/predictive-maintenance-dataset-ai4i-2020
- **Size**: 10,000 records
- **Features**: Air temp, process temp, RPM, torque, tool wear, failure labels
- **Titan mapping**: PHX-CNC-001 through PHX-CNC-010

### NASA Turbofan C-MAPSS ⭐ TOP PICK
**For Remaining Useful Life prediction**

- **URL**: https://www.kaggle.com/datasets/behrad3d/nasa-cmaps
- **Features**: 21 sensors, run-to-failure trajectories, known RUL
- **Titan use**: Train maintenance agent's RUL model

---

## 📦 Supply Chain Data

### DataCo SMART Supply Chain ⭐ TOP PICK
**Covers inventory, orders, and logistics**

- **URL**: https://www.kaggle.com/datasets/shashwatwork/dataco-smart-supply-chain-for-big-data-analysis
- **Size**: 180,000+ records
- **Features**: Orders, products, shipping, customers
- **Titan mapping**: Map to Boeing, Tesla, GE customers

---

## 🚀 Quick Start

```bash
# Install Kaggle CLI
pip install kaggle

# Download datasets
kaggle datasets download -d stephanmatzka/predictive-maintenance-dataset-ai4i-2020
kaggle datasets download -d shashwatwork/dataco-smart-supply-chain-for-big-data-analysis

# Unzip
unzip "*.zip" -d data/
```

## Titan Data Mapping

```python
# Map AI4I data to Titan equipment
df['equipment_id'] = 'PHX-CNC-' + (df.index % 10 + 1).astype(str).str.zfill(3)
df['facility_id'] = 'PHX'

# Map DataCo customers to Titan accounts  
customer_map = {
    'Consumer': 'CUST-STANDARD',
    'Corporate': 'CUST-BOEING',
    'Home Office': 'CUST-TESLA'
}
```

With these two datasets, you can simulate all Titan 5.0 demo scenarios!
