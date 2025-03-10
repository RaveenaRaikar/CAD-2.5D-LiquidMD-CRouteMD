The FPGA CAD Framework for 2.5 architectures
==============================

An FPGA CAD framework focused on rapid prototyping of new CAD algorithms.
The framework is implemented in Java. At this moment packing, placement and routing algorithms are implemented in the framework. This branch contains the adaptations for 2.5D PnR.


What can I do with this tool flow?
---------------

<ul>

<li>
Partitioning and Packing:
<ul>
  <li>Partitioning the circuit for 2.5D architecture followed by packing with AAPack (VTR8). </li>
</ul>
</li>

<li>
Placement:
<ul>
  <li> Modular Placement with LiquidMD: based on Liquid and optimized for 2.5D architectures</li>
</ul>
</li>

<li>
Routing:
<ul>
  <li>Connection-based routing with CRouteMD : based on CRoute and implemented for 2.5D architectures</li>
</ul>
</li>

</ul>

Usage
---------------

Some parts of this toolflow require external packages. Please contact us if you want more information.

To calculate point to point delays, vpr is used (see option --vpr_command). When compiling vpr, the macro PRINT_ARRAYS has to be defined in "place/timing_place_lookup.c".

License
---------------
see license file

Contact us
---------------
The FPGA Placement Framework is released by Ghent University, ELIS department, Hardware and Embedded Systems (HES) group (http://hes.elis.ugent.be).

If you encounter bugs, want to use the FPGA CAD Framework but need support or want to tell us about your results, please contact us.

Referencing the FPGA Placement Framework
---------------
If you use the FPGA CAD Framework in your work, please reference the following papers in your publications: <br>

Partitioning and Packing :
<b>Multi-die heterogeneous FPGAs : how balanced should netlist partitioning be?<br>
Raveena Raikar and Dirk Stroobandt</b> <br>

Placement:
<b>Modularity driven parallel placement algorithm for 2.5D FPGA architectures<br>
Raveena Raikar and Dirk Stroobandt</b> <br>
<b>LiquidMD: Optimizing Inter-die and Intra-die placement for 2.5D FPGA Architectures<br>
Raveena Raikar and Dirk Stroobandt</b> <br>

Routing:
<b>Routing in 2.5D FPGAs: How long should interposer lines be?<br>
Raveena Raikar and Dirk Stroobandt</b> <br>

Contributors
---------------
Active Contributors
<ul>
  <li>Raveena Raikar - <a href="mailto:raveenaramanand.raikar@ugent.be">raveenaramanand.raikar@ugent.be</a></li>
</ul>


Development
---------------
The FPGA CAD Framework is a work in progress, input is welcome.

