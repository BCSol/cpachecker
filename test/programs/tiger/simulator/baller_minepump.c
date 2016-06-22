/* Generated by CIL v. 1.3.7 */
/* print_CIL_Input is true */

#include <stdio.h>
//#include <string.h>
#include <stdlib.h>

//#define PRINTING //Allows for printing of Mails. Quite usefull for debuging etc.
//#define LOCAL //Activate for local random number generator and valid program configurations for each run. Deactivate when using CPA

#ifdef LOCAL
int __VERIFIER_nondet_int() {
	return rand();
}
#endif

#ifndef LOCAL
extern int __VERIFIER_nondet_int();
#endif

int helper = 0;

int __SELECTED_FEATURE_base;
int __SELECTED_FEATURE_highWaterSensor;
int __SELECTED_FEATURE_lowWaterSensor;
int __SELECTED_FEATURE_methaneQuery;
int __SELECTED_FEATURE_methaneAlarm;
int __SELECTED_FEATURE_stopCommand;
int __SELECTED_FEATURE_startCommand;
int __GUIDSL_ROOT_PRODUCTION;

void waterRise(void);
void changeMethaneLevel(void);
void startSystem(void);
void stopSystem(void);
void timeShift(void);
void cleanup(void);
void lowerWaterLevel(void);
int isMethaneLevelCritical(void);
void printEnvironment(void);
int isHighWaterSensorDry(void);
int isLowWaterSensorDry(void);
void activatePump(void);
void deactivatePump(void);
int isPumpRunning(void);
void printPump(void);
void processEnvironment(void);
int isHighWaterLevel(void);
int isLowWaterLevel(void);
int isMethaneAlarm(void);
void select_features(void);
void select_helpers(void);
int valid_product(void);
int getWaterLevel(void);

void __automaton_fail(void);
void __utac_acc__Specification1_spec__1(void);

void __automaton_fail(void)
{
	goto error;
error: helper = helper + 1;
	return;
}

void __utac_acc__Specification1_spec__1(void)
{
	if (isMethaneLevelCritical()) {
		if (isPumpRunning()) {
			__automaton_fail();
		}
		else {

		}
	}
	else {

	}
	return;
}

int methAndRunningLastTime;

void __utac_acc__Specification2_spec__1(void)
{
		methAndRunningLastTime = 0;
		return;
}

void __utac_acc__Specification2_spec__2(void)
{
	if (isMethaneLevelCritical()) {
		if (isPumpRunning()) {
			if (methAndRunningLastTime) {
				__automaton_fail();
			}
			else {
				methAndRunningLastTime = 1;
			}
		}
		else {
			methAndRunningLastTime = 0;
		}
	}
	else {
		methAndRunningLastTime = 0;
	}
	return;
}

void __utac_acc__Specification3_spec__1(void)
{
	if (isMethaneLevelCritical()) {

	}
	else {
		if (getWaterLevel() == 2) {
			if (isPumpRunning()) {

			}
			else {
				__automaton_fail();
			}
		}
		else {

		}
	}
	return;
}

__inline void __utac_acc__Specification4_spec__1(void)
{
	if (getWaterLevel() == 0) {
		if (isPumpRunning()) {
			__automaton_fail();
		}
		else {

		}
	}
	else {

	}
	return;
}

int switchedOnBeforeTS;

void __utac_acc__Specification5_spec__1(void)
{
	switchedOnBeforeTS = 0;
	return;
}

void __utac_acc__Specification5_spec__2(void)
{
	switchedOnBeforeTS = isPumpRunning();
	return;
}

void __utac_acc__Specification5_spec__3(void)
{
	if (getWaterLevel() != 2) {
		if (isPumpRunning()) {
			if (!switchedOnBeforeTS) {
				__automaton_fail();
			}
			else {

			}
		}
		else {

		}
	}
	else {

	}
	return;
}

void test(void)
{
	if (__VERIFIER_nondet_int()) {
		waterRise();
	}
	else {

	}
	if (__VERIFIER_nondet_int()) {
		changeMethaneLevel();
	}
	else {

	}
	if (__VERIFIER_nondet_int()) {
		if (__SELECTED_FEATURE_startCommand) {
			startSystem();
		}
		else {

		}
	}
	else {
		if (__VERIFIER_nondet_int()) {
			if (__SELECTED_FEATURE_stopCommand) {
				stopSystem();
			}
			else {

			}
		}
		else {

		}
	}
	timeShift();
	cleanup();
	return;
}

int pumpRunning = 0;
int systemActive = 1;

void timeShift(void)
{
	__utac_acc__Specification5_spec__2();
	if (pumpRunning) {
		lowerWaterLevel();
	}
	else {

	}
	if (systemActive) {
		processEnvironment();
	}
	else {

	}
__utac_acc__Specification1_spec: __utac_acc__Specification1_spec__1();
__utac_acc__Specification2_spec: __utac_acc__Specification2_spec__2();
__utac_acc__Specification3_spec: __utac_acc__Specification3_spec__1();
__utac_acc__Specification4_spec: __utac_acc__Specification4_spec__1();
__utac_acc__Specification5_spec: __utac_acc__Specification5_spec__3();
	return;
}

void processEnvironment__before__highWaterSensor(void)
{
		return;
}

void processEnvironment__role__highWaterSensor(void)
{
	if (!pumpRunning) {
		if (isHighWaterLevel()) {
			activatePump();
		}
		else {
			processEnvironment__before__highWaterSensor();
		}
	}
	else {
		processEnvironment__before__highWaterSensor();
	}
	return;
}

void processEnvironment__before__lowWaterSensor(void)
{
	if (__SELECTED_FEATURE_highWaterSensor) {
		processEnvironment__role__highWaterSensor();
		return;
	}
	else {
		processEnvironment__before__highWaterSensor();
		return;
	}
}

void processEnvironment__role__lowWaterSensor(void)
{
		if (pumpRunning) {
			if (isLowWaterLevel()) {
						deactivatePump();
			}
			else {
						processEnvironment__before__lowWaterSensor();
			}
		}
		else {
					processEnvironment__before__lowWaterSensor();
		}
		return;
}

void processEnvironment__before__methaneAlarm(void)
{
	if (__SELECTED_FEATURE_lowWaterSensor) {
		processEnvironment__role__lowWaterSensor();
		return;
	}
	else {
		processEnvironment__before__lowWaterSensor();
		return;
	}
}

void processEnvironment__role__methaneAlarm(void)
{
	if (pumpRunning) {
		if (isMethaneAlarm()) {
			deactivatePump();
		}
		else {
			processEnvironment__before__methaneAlarm();
		}
	}
	else {
		processEnvironment__before__methaneAlarm();
	}
	return;
}

void processEnvironment(void)
{
	if (__SELECTED_FEATURE_methaneAlarm) {
		processEnvironment__role__methaneAlarm();
		return;
	}
	else {
		processEnvironment__before__methaneAlarm();
		return;
	}
}

void activatePump__before__methaneQuery(void)
{
		pumpRunning = 1;
		return;
}

void activatePump__role__methaneQuery(void)
{
	if (isMethaneAlarm()) {

	}
	else {
		activatePump__before__methaneQuery();
	}
	return;
}

void activatePump(void)
{
	if (__SELECTED_FEATURE_methaneQuery) {
		activatePump__role__methaneQuery();
		return;
	}
	else {
		activatePump__before__methaneQuery();
		return;
	}
}

void deactivatePump(void)
{
	pumpRunning = 0;
	return;
}

int isMethaneAlarm(void)
{
	int retValue_acc;
	retValue_acc = isMethaneLevelCritical();
	return (retValue_acc);
}

int isPumpRunning(void)
{
	int retValue_acc;
	retValue_acc = pumpRunning;
	return (retValue_acc);
}

void printPump(void)
{
	printf("Pump(System:");
	if (systemActive) {
		printf("On");
	}
	else {
		printf("Off");
	}
	printf(",Pump:");
	if (pumpRunning) {
		printf("On");
	}
	else {
		printf("Off");
	}
	printf(") ");
	printEnvironment();
	printf("\n");
	return;
}

int isHighWaterLevel(void)
{
	int retValue_acc;
	if (isHighWaterSensorDry()) {
		retValue_acc = 0;
	}
	else {
		retValue_acc = 1;
	}
	return (retValue_acc);
}

int isLowWaterLevel(void)
{
	int retValue_acc;
	if (isLowWaterSensorDry()) {
		retValue_acc = 0;
	}
	else {
		retValue_acc = 1;
	}
	return (retValue_acc);
}

void stopSystem(void)
{
	if (pumpRunning) {
		deactivatePump();
	}
	else {

	}
	systemActive = 0;
	return;
}

void startSystem(void)
{
	systemActive = 1;
	return;
}

void select_features(void)
{
	__SELECTED_FEATURE_base = 1;
	__SELECTED_FEATURE_highWaterSensor = __VERIFIER_nondet_int();
	__SELECTED_FEATURE_lowWaterSensor = __VERIFIER_nondet_int();
	__SELECTED_FEATURE_methaneQuery = __VERIFIER_nondet_int();
	__SELECTED_FEATURE_methaneAlarm = __VERIFIER_nondet_int();
	__SELECTED_FEATURE_stopCommand = __VERIFIER_nondet_int();
	__SELECTED_FEATURE_startCommand = __VERIFIER_nondet_int();
	return;
}

void select_helpers(void)
{
	__GUIDSL_ROOT_PRODUCTION = 1;
	return;
}

int valid_product(void)
{
	int retValue_acc;

	retValue_acc = __SELECTED_FEATURE_base;
	return (retValue_acc);
}

int waterLevel = 1;
int methaneLevelCritical = 0;

void lowerWaterLevel(void)
{
	if (waterLevel > 0) {
		waterLevel = waterLevel - 1;
	}
	else {

	}
	return;
}

void waterRise(void)
{
	if (waterLevel < 2) {
		waterLevel = waterLevel + 1;
	}
	else {

	}
	return;
}

void changeMethaneLevel(void)
{
	if (methaneLevelCritical) {
		methaneLevelCritical = 0;
	}
	else {
		methaneLevelCritical = 1;
	}
	return;
}

int isMethaneLevelCritical(void)
{
	int retValue_acc;
	retValue_acc = methaneLevelCritical;
	return (retValue_acc);
}

void printEnvironment(void)
{
	printf("Env(Water:%i", waterLevel);
	printf(",Meth:");
	if (methaneLevelCritical) {
		printf("CRIT");
	}
	else {
		printf("OK");
	}
	printf(")");
	return;
}

int getWaterLevel(void)
{
	int retValue_acc;
	retValue_acc = waterLevel;
	return (retValue_acc);
}

int isHighWaterSensorDry(void)
{
	int retValue_acc;
		if (waterLevel < 2) {
			retValue_acc = 1;
			return (retValue_acc);
		}
		else {
			retValue_acc = 0;
			return (retValue_acc);
		}
}

int isLowWaterSensorDry(void)
{
	int retValue_acc;
	retValue_acc = waterLevel == 0;
	return (retValue_acc);
}

int cleanupTimeShifts = 4;

void cleanup(void)
{
	int i;
	int __cil_tmp2;
	timeShift();
	i = 0;
	while (1) {
	while_4_continue: /* CIL Label */;
	{
		__cil_tmp2 = cleanupTimeShifts - 1;
		if (i < __cil_tmp2) {

		}
		else {
			goto while_4_break;
		}
	}
	{
		timeShift();
		i = i + 1;
	}
	}
while_4_break: /* CIL Label */;
	return;
}

void Specification2(void)
{
	timeShift();
	printPump();
	timeShift();
	printPump();
	timeShift();
	printPump();
	waterRise();
	printPump();
	timeShift();
	printPump();
	changeMethaneLevel();
	printPump();
	timeShift();
	printPump();
	cleanup();
	return;
}

void setup(void)
{
	return;
}

void runTest(void)
{
	__utac_acc__Specification2_spec__1();
	__utac_acc__Specification5_spec__1();
	test();
	return;
}

int main(void)
{
	select_helpers();
	select_features();
	if (valid_product()) {
		setup();
		runTest();
	}
	else {

	}
	return 0;
}