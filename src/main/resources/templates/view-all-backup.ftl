<#include "template-header.ftl">
<#include "template-footer.ftl">
<@header/>
<#if backupExists ?? && backupExists>
<div class="row-fluid">
	<div class="span12">
	<center><h3>View all backups</h3></center>
	</div>
</div><br>
<div class="row-fluid">
	<div id="container" class="offset2 span8">
		
	</div>
</div>
<script>
	$(document).ready(function(){
		molgenis.retrieveBackups($('#container'));
	});
</script>
<#else>
<div class="row-fluid">
	<div class="span12">
		<center>Back up does not exist!</center>
	</div>
</div>
</#if>
<@footer/>