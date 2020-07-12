'use strict';

const chai = require('chai');
const expect = chai.expect;

const transitionDuration = 5;
const textPadding = 5;

const MAXIMUM_DELTA = 0.0001;

const Vector = require('../../../../main/app/graph/infrastructure/vectors').Vector;

const guiElementsMock = require('../testinfrastructure/gui-elements-mock');
const AppContext = require('../../../../main/app/graph/app-context');
const Dependencies = AppContext.newInstance({guiElements: guiElementsMock, transitionDuration, textPadding}).getDependencies();
//const {buildFilterCollection} = require('../../../../main/app/graph/filter');
const createDependencies = require('../testinfrastructure/dependencies-test-infrastructure').createDependencies;

const DependenciesUi = require('./testinfrastructure/dependencies-ui').DependenciesUi;


describe('Dependencies', () => {

  describe('#recreateVisible', () => {

    let dependencies;
    let dependenciesUi;

    before(async () => {
      dependencies = createDependencies(Dependencies,
        'my.company.somePkg.FirstClass-my.company.otherPkg.FirstClass',
        'my.company.somePkg.SecondClass-my.company.otherPkg.SecondClass',
        'my.company.somePkg.SecondClass$SomeInnerClass-my.company.otherPkg.SecondClass',
        'my.company.somePkg.SecondClass$SomeInnerClass-my.company.otherPkg.ThirdClass'
      );
      dependencies.recreateVisible();
      await dependencies.createListener().onLayoutChanged();
      dependenciesUi = DependenciesUi.of(dependencies);
    });

    it('displays all dependencies', () => {
      dependenciesUi.expectToShowDependencies(
        'my.company.somePkg.FirstClass-my.company.otherPkg.FirstClass',
        'my.company.somePkg.SecondClass-my.company.otherPkg.SecondClass',
        'my.company.somePkg.SecondClass$SomeInnerClass-my.company.otherPkg.SecondClass',
        'my.company.somePkg.SecondClass$SomeInnerClass-my.company.otherPkg.ThirdClass'
      );
    });

    it('puts all dependencies in front of both end nodes', () => {
      dependenciesUi.visibleDependencyUis.forEach(dependencyUi => {
        dependencyUi.expectToLieInFrontOf(dependencyUi.originNodeSvgElement);
        dependencyUi.expectToLieInFrontOf(dependencyUi.targetNodeSvgElement);
      });
    });

    it('places all dependencies directly between the circles of their end nodes', () => {
      dependenciesUi.visibleDependencyUis.forEach(visibleDependencyUi => {
        const originCircle = visibleDependencyUi._dependency.originNode.absoluteFixableCircle;
        const targetCircle = visibleDependencyUi._dependency.targetNode.absoluteFixableCircle;
        const circleMiddleDistance = Vector.between(originCircle, targetCircle).length();
        const expectedDependencyLength = circleMiddleDistance - (originCircle.r + targetCircle.r);

        expect(visibleDependencyUi.line.lineLength).to.be.closeTo(expectedDependencyLength, MAXIMUM_DELTA);

        visibleDependencyUi.expectToTouchOriginNode();
        visibleDependencyUi.expectToTouchTargetNode();
      });
    });

    it('places mutual dependencies parallel with a small distance to each other', async () => {
      const dependencies = createDependencies(Dependencies,
        'my.company.FirstClass-my.company.SecondClass',
        'my.company.SecondClass-my.company.FirstClass',
      );
      dependencies.recreateVisible();
      await dependencies.createListener().onLayoutChanged();
      const dependenciesUi = DependenciesUi.of(dependencies);

      dependenciesUi.visibleDependencyUis.forEach(dependencyUi => {
        dependencyUi.expectToTouchOriginNode();
        dependencyUi.expectToTouchTargetNode();
      });
    });
  });

  describe('can display violations', () => {
    it('#showViolation()', async () => {
      const dependencies = createDependencies(Dependencies,
      'my.company.FirstClass-my.company.SecondClass',
      'my.company.SecondClass-my.company.FirstClass',
      );
      dependencies.recreateVisible();
      await dependencies.createListener().onLayoutChanged();
      const dependenciesUi = DependenciesUi.of(dependencies);

      dependencies.showViolations({rule: 'rule1', violations: ['Method <my.company.FirstClass.startMethod()> calls method <my.company.SecondClass.targetMethod()> in (SomeClass.java:0)']});
      dependencies.recreateVisible();

      dependenciesUi.visibleDependencyUis[0].expectToBeMarkedAsViolation();
      dependenciesUi.visibleDependencyUis[1].expectToNotBeMarkedAsViolation();

      dependencies.showViolations({rule: 'rule2', violations: ['Method <my.company.SecondClass.startMethod()> calls method <my.company.FirstClass.targetMethod()> in (SomeClass.java:1)']});
      dependencies.recreateVisible();

      // dependenciesUi.visibleDependencyUis.forEach(dependencyUi => dependencyUi.expectToBeMarkedAsViolation());
      dependenciesUi.visibleDependencyUis[0].expectToBeMarkedAsViolation();
      dependenciesUi.visibleDependencyUis[1].expectToBeMarkedAsViolation();
    });

    it('#hideViolation()', async () => {
      const dependencies = createDependencies(Dependencies,
      'my.company.FirstClass-my.company.SecondClass',
      'my.company.SecondClass-my.company.FirstClass',
      );
      dependencies.recreateVisible();
      await dependencies.createListener().onLayoutChanged();
      const dependenciesUi = DependenciesUi.of(dependencies);

      dependencies.showViolations({rule: 'rule1', violations: ['Method <my.company.FirstClass.startMethod()> calls method <my.company.SecondClass.targetMethod()> in (SomeClass.java:0)']});
      dependencies.showViolations({rule: 'rule2', violations: ['Method <my.company.SecondClass.startMethod()> calls method <my.company.FirstClass.targetMethod()> in (SomeClass.java:1)']});
      dependencies.recreateVisible();

      dependenciesUi.visibleDependencyUis.forEach(dependencyUi => dependencyUi.expectToBeMarkedAsViolation());

      dependencies.hideViolations({rule: 'rule2', violations: ['Method <my.company.SecondClass.startMethod()> calls method <my.company.FirstClass.targetMethod()> in (SomeClass.java:1)']});
      dependencies.recreateVisible();

      dependenciesUi.visibleDependencyUis[0].expectToBeMarkedAsViolation();
      dependenciesUi.visibleDependencyUis[1].expectToNotBeMarkedAsViolation();

      dependencies.hideViolations({rule: 'rule1', violations: ['Method <my.company.FirstClass.startMethod()> calls method <my.company.SecondClass.targetMethod()> in (SomeClass.java:0)']});
      dependencies.recreateVisible();

      dependenciesUi.visibleDependencyUis.forEach(dependencyUi => dependencyUi.expectToNotBeMarkedAsViolation());
    });
  });

  // filtering by type tested in graph-test
  // filtering by violations tested in graph-test
  // filtering of the nodes which also applies to the dependencies tested in graph-test
});