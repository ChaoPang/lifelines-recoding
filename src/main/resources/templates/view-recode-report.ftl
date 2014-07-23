<#include "template-header.ftl">
<#include "template-footer.ftl">
<@header/>
<script src="/js/view-recode.js"></script>
<#if hidForm?? && !hidForm>
<div class="row-fluid">
	<div class="span12">
	<center><h3>Recode data</h3></center>
	</div>
</div><br><br>
<div class="row-fluid">
	<div class="span12">
		<center>Upload a dataset that needs to be recoded in <u>excel</u> format. Please include individual identifiers in dataset for reference.</center>
	</div>
</div><br>
<form method="POST" enctype="multipart/form-data">
	<div class="row-fluid">
		<div class="span12">
			<center>
				<div class="fileupload fileupload-new" data-provides="fileupload">
					<div class="input-append">
						<div class="uneditable-input">
							<i class="icon-file fileupload-exists"></i>
							<span class="fileupload-preview"></span>
						</div>
						<span class="btn btn-file btn-info">
							<span class="fileupload-new">Select file</span>
							<span class="fileupload-exists">Change</span>
							<input type="file" name="file" required/>
						</span>
						<a href="#" class="btn btn-danger fileupload-exists" data-dismiss="fileupload">Remove</a>
						<button id="upload-button" class="btn btn-primary" type="button">Upload</button>
					</div>
				</div>
			</center>
		</div>
	</div>
</form>
<script>
	$(document).ready(function(){
		$('#upload-button').click(function(event){
			$('form:first').attr('action','/recode/upload').submit();
		});
	});
</script>
<#else>
<div class="row-fluid">
	<div class="span12">
	<center><h3>Report for recoding</h3></center>
	</div>
</div><br><br>
<div class="row-fluid">
	<div class="offset4 span4 well">
		<div id="matched-container" class="row-fluid"></div><br>
		<div id="unmatched-container" class="row-fluid"></div>		
	</div>
	<div class="span4">
		<button id="finished-button" class="btn btn-float-right" type="button">Finish recoding</button>
	</div>
</div>
<div id="result-container" class="row-fluid"></div>
<script>
	$(document).ready(function(){
		molgenis.retrieveResult($('#matched-container'), $('#unmatched-container'), $('#result-container'));
	});
</script>
</#if>
<@footer/>